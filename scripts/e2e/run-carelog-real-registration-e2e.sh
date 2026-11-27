#!/usr/bin/env bash
set -euo pipefail

# Carelog registration through platform-core against the REAL carelog-be (no stub).
# Chain: Gateway -> platform-core (Carelog-only composition, dev profile, Finance disabled)
#   -> carelog-be enrollment PUT (Core's chunked JDK HttpClient request) and claims GET on login.
# Separate databases: carelog-be has its own DB (carelog_be_e2e); Core has platform_e2e/identity.

carelog_source="${1:?carelog-be source checkout is required}"
platform_source="${2:?platform-services source checkout is required}"
: "${RUNNER_TEMP:?Hosted runner temp directory is required}"
: "${JAVA_HOME_17_X64:?Java 17 is required for carelog-be and Gateway}"
: "${JAVA_HOME_21_X64:?Java 21 is required for platform-core build and run}"
# Machine-checkable disclosure: this job must target the real carelog-be, never a stub.
test "${CARELOG_ENROLLMENT_TARGET:-}" = "real" || {
  echo "CARELOG_ENROLLMENT_TARGET must be 'real' (the only supported target for this job)." >&2
  exit 1
}
echo "CARELOG_ENROLLMENT_TARGET=real (carelog-be enrollment and claims, no stub)"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/carelog-real-registration-e2e.mjs"

carelog_port=18090
core_port=18081
core_management_port=19092
gateway_port=18080
gateway_management_port=19091
dead_port=9

log_dir="$RUNNER_TEMP/carelog-real-registration-e2e"
carelog_access_dir="$log_dir/carelog-access"
mkdir -m 700 -p "$log_dir" "$carelog_access_dir"

export PGHOST=127.0.0.1 PGPORT=5432 PGUSER=postgres PGDATABASE=postgres
for _ in $(seq 1 60); do
  if pg_isready -q; then break; fi
  sleep 1
done
pg_isready -q

# Per-run secrets. They live only in process environments and are masked before any command can echo them.
identity_db_password="$(openssl rand -hex 32)"
carelog_db_password="$(openssl rand -hex 32)"
identity_jwt_key="$(openssl rand -hex 64)"
carelog_jwt_key="$(openssl rand -hex 64)"
product_claims_secret="$(openssl rand -hex 32)"
carelog_enrollment_credential="$(openssl rand -hex 32)"
carelog_gateway_internal_secret="$(openssl rand -hex 32)"
gateway_legacy_jwt_key="$(openssl rand -hex 32)"
gateway_internal_secret="$(openssl rand -hex 32)"
finance_trusted_context_proof="$(openssl rand -hex 32)"
for secret in "$identity_db_password" "$carelog_db_password" "$identity_jwt_key" "$carelog_jwt_key" \
  "$product_claims_secret" "$carelog_enrollment_credential" "$carelog_gateway_internal_secret" \
  "$gateway_legacy_jwt_key" "$gateway_internal_secret" "$finance_trusted_context_proof"; do
  printf '::add-mask::%s\n' "$secret"
done

# Separate roles and databases for carelog-be and Core. Core keeps its identity schema.
psql -v ON_ERROR_STOP=1 -v identity_password="$identity_db_password" -v carelog_password="$carelog_db_password" <<'SQL'
CREATE ROLE identity_e2e LOGIN PASSWORD :'identity_password';
CREATE ROLE carelog_e2e LOGIN PASSWORD :'carelog_password';
CREATE DATABASE platform_e2e OWNER identity_e2e;
CREATE DATABASE carelog_be_e2e OWNER carelog_e2e;
SQL
psql -v ON_ERROR_STOP=1 -d platform_e2e -c 'CREATE SCHEMA identity AUTHORIZATION identity_e2e;'

# carelog-be runs on Java 17 (its Gradle toolchain). platform-services builds on Java 21 and runs Gateway on 17.
(
  export JAVA_HOME="$JAVA_HOME_17_X64"
  export PATH="$JAVA_HOME/bin:$PATH"
  cd "$carelog_source"
  ./gradlew --no-daemon --console=plain :carelog-be:bootJar
)
export JAVA_HOME="$JAVA_HOME_21_X64"
export PATH="$JAVA_HOME/bin:$PATH"
"$platform_source/gradlew" --no-daemon --console=plain -p "$platform_source" bootJar

find_boot_jar() {
  local dir="$1" result
  result="$(find "$dir" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"
  [[ -n "$result" && -f "$result" ]]
  printf '%s' "$result"
}
carelog_jar="$(find_boot_jar "$carelog_source/carelog-be/build/libs")"
core_jar="$(find_boot_jar "$platform_source/platform-core/app/build/libs")"
gateway_jar="$(find_boot_jar "$platform_source/gateway-app/build/libs")"

wait_ready() {
  local url="$1" label="$2"
  for _ in $(seq 1 150); do
    if curl --fail --silent --output /dev/null "$url"; then return 0; fi
    sleep 2
  done
  printf '%s readiness failed; process logs remain on the ephemeral runner only.\n' "$label" >&2
  return 1
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  # On failure, surface only ERROR/WARN lines and method/status/chunking fields (no paths, IDs or bodies).
  if [[ "$status" -ne 0 ]]; then
    for log in "$log_dir/carelog.log" "$log_dir/core.log" "$log_dir/gateway.log"; do
      if [[ -f "$log" ]]; then
        echo "--- ${log##*/}: last ERROR/WARN lines"
        grep -E ' (ERROR|WARN) ' "$log" | tail -n 40 || true
      fi
    done
    if compgen -G "$carelog_access_dir/access_log*" >/dev/null; then
      echo "--- carelog-be access log: method, status, Content-Length, Transfer-Encoding only"
      cat "$carelog_access_dir"/access_log* | awk '{print $1, $3, $4, $5}' | tail -n 60 || true
    fi
  fi
  for pid in "${gateway_pid:-}" "${core_pid:-}" "${carelog_pid:-}"; do
    if [[ -n "$pid" ]]; then kill "$pid" 2>/dev/null || true; fi
  done
  wait 2>/dev/null || true
  exit "$status"
}
trap cleanup EXIT INT TERM

# carelog-be: real enrollment and claims. Enrollment/claims secrets are distinct from the gateway secret.
# Access log records per request Content-Length and Transfer-Encoding so the chunked PUT can be observed.
nohup env \
  SERVER_ADDRESS=127.0.0.1 \
  SERVER_PORT="$carelog_port" \
  SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/carelog_be_e2e?connectTimeout=3&socketTimeout=10' \
  SPRING_DATASOURCE_USERNAME=carelog_e2e \
  SPRING_DATASOURCE_PASSWORD="$carelog_db_password" \
  REDIS_HOST=127.0.0.1 \
  REDIS_PORT=6379 \
  SPRING_DATA_REDIS_DATABASE=2 \
  JWT_SECRET_KEY="$carelog_jwt_key" \
  JWT_ACCESS_TOKEN_VALIDITY=900000 \
  JWT_REFRESH_TOKEN_VALIDITY=3600000 \
  GATEWAY_INTERNAL_SECRET="$carelog_gateway_internal_secret" \
  CARELOG_INTERNAL_IDENTITY_CLAIMS_ENABLED=true \
  CARELOG_INTERNAL_IDENTITY_CLAIMS_SERVICE_SECRET="$product_claims_secret" \
  CARELOG_IDENTITY_ENROLLMENT_ENABLED=true \
  CARELOG_IDENTITY_ENROLLMENT_SERVICE_SECRET="$carelog_enrollment_credential" \
  SERVER_TOMCAT_ACCESSLOG_ENABLED=true \
  SERVER_TOMCAT_ACCESSLOG_BUFFERED=false \
  SERVER_TOMCAT_ACCESSLOG_DIRECTORY="$carelog_access_dir" \
  SERVER_TOMCAT_ACCESSLOG_PREFIX=access_log \
  SERVER_TOMCAT_ACCESSLOG_PATTERN='%m %U %s cl=%{Content-Length}i te=%{Transfer-Encoding}i' \
  "$JAVA_HOME_17_X64/bin/java" -jar "$carelog_jar" >"$log_dir/carelog.log" 2>&1 &
carelog_pid=$!

# carelog-be closes /actuator/health to anonymous callers (403), so readiness is "Spring started" plus any HTTP answer.
wait_carelog_ready() {
  local code
  for _ in $(seq 1 150); do
    if grep -q 'Started CarelogApplication' "$log_dir/carelog.log" 2>/dev/null; then
      code="$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$carelog_port/api/v1/actuator/health" || true)"
      if [[ -n "$code" && "$code" != "000" ]]; then return 0; fi
    fi
    sleep 2
  done
  echo "carelog-be readiness failed; process logs remain on the ephemeral runner only." >&2
  return 1
}
wait_carelog_ready

# platform-core, Carelog-only composition: dev profile (required by Carelog registration), Finance reference off.
# Enrollment PUT and product-claims GET both target the real carelog-be origin (no path in the base URL).
nohup env \
  SPRING_PROFILES_ACTIVE=dev \
  PLATFORM_ENVIRONMENT=dev \
  PLATFORM_CORE_PORT="$core_port" \
  PLATFORM_CORE_MANAGEMENT_PORT="$core_management_port" \
  SERVER_ADDRESS=127.0.0.1 \
  MANAGEMENT_SERVER_ADDRESS=127.0.0.1 \
  PLATFORM_IDENTITY_AUTH_ENABLED=true \
  PLATFORM_IDENTITY_PERSISTENCE_ENABLED=true \
  PLATFORM_IDENTITY_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/platform_e2e?currentSchema=identity&connectTimeout=3&socketTimeout=10' \
  PLATFORM_IDENTITY_DATASOURCE_USERNAME=identity_e2e \
  PLATFORM_IDENTITY_DATASOURCE_PASSWORD="$identity_db_password" \
  PLATFORM_IDENTITY_FLYWAY_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/platform_e2e?currentSchema=identity&connectTimeout=3&socketTimeout=10' \
  PLATFORM_IDENTITY_FLYWAY_DATASOURCE_USERNAME=identity_e2e \
  PLATFORM_IDENTITY_FLYWAY_DATASOURCE_PASSWORD="$identity_db_password" \
  PLATFORM_IDENTITY_REDIS_HOST=127.0.0.1 \
  PLATFORM_IDENTITY_REDIS_PORT=6379 \
  PLATFORM_IDENTITY_REDIS_KEY_PREFIX=carelog-real-e2e:identity: \
  PLATFORM_IDENTITY_JWT_SECRET_KEY="$identity_jwt_key" \
  PLATFORM_IDENTITY_JWT_ACCESS_TOKEN_VALIDITY=900000 \
  PLATFORM_IDENTITY_JWT_REFRESH_TOKEN_VALIDITY=3600000 \
  PLATFORM_IDENTITY_PRODUCT_CLAIMS_ENABLED=true \
  PLATFORM_IDENTITY_PRODUCT_CLAIMS_BASE_URL="http://127.0.0.1:$carelog_port" \
  PLATFORM_IDENTITY_PRODUCT_CLAIMS_SERVICE_SECRET="$product_claims_secret" \
  PLATFORM_IDENTITY_KAKAO_CLIENT_ID=carelog-real-e2e-unused \
  PLATFORM_IDENTITY_KAKAO_CLIENT_SECRET=carelog-real-e2e-unused-secret \
  PLATFORM_IDENTITY_KAKAO_AUTHORIZATION_URI="http://127.0.0.1:$dead_port/oauth/authorize" \
  PLATFORM_IDENTITY_KAKAO_TOKEN_URI="http://127.0.0.1:$dead_port/oauth/token" \
  PLATFORM_IDENTITY_KAKAO_USER_INFO_URI="http://127.0.0.1:$dead_port/v2/user/me" \
  PLATFORM_IDENTITY_OAUTH_KAKAO_WEB_REDIRECT_URI="http://carelog-real-e2e.invalid/oauth/kakao/callback" \
  PLATFORM_IDENTITY_OAUTH_KAKAO_MOBILE_REDIRECT_URI="http://carelog-real-e2e.invalid/oauth/kakao/callback" \
  PLATFORM_IDENTITY_OAUTH_ALLOWED_RETURN_ORIGINS="http://carelog-real-e2e.invalid" \
  PLATFORM_IDENTITY_CARELOG_REGISTRATION_ENABLED=true \
  PLATFORM_IDENTITY_CARELOG_ENROLLMENT_ENABLED=true \
  PLATFORM_IDENTITY_CARELOG_ENROLLMENT_BASE_URL="http://127.0.0.1:$carelog_port" \
  PLATFORM_IDENTITY_CARELOG_ENROLLMENT_CREDENTIAL="$carelog_enrollment_credential" \
  PLATFORM_IDENTITY_FINANCE_REFERENCE_ENABLED=false \
  "$JAVA_HOME_21_X64/bin/java" -jar "$core_jar" >"$log_dir/core.log" 2>&1 &
core_pid=$!

wait_ready "http://127.0.0.1:$core_management_port/actuator/health/readiness" Core

# Gateway: Carelog registration and login routes to Core. Finance and the unused carelog-be/RAG routes
# point at a closed port (Finance is disabled in this composition).
nohup env \
  PLATFORM_ENVIRONMENT=dev \
  PLATFORM_GATEWAY_PORT="$gateway_port" \
  SERVER_ADDRESS=127.0.0.1 \
  MANAGEMENT_SERVER_ADDRESS=127.0.0.1 \
  PLATFORM_GATEWAY_MANAGEMENT_PORT="$gateway_management_port" \
  PLATFORM_GATEWAY_REDIS_HOST=127.0.0.1 \
  PLATFORM_GATEWAY_REDIS_PORT=6379 \
  PLATFORM_GATEWAY_REDIS_KEY_PREFIX=carelog-real-e2e:gateway: \
  SPRING_DATA_REDIS_TIMEOUT=2s \
  PLATFORM_GATEWAY_AUTH_URI="http://127.0.0.1:$core_port" \
  PLATFORM_GATEWAY_FINANCE_BE_URI="http://127.0.0.1:$dead_port" \
  PLATFORM_GATEWAY_CARELOG_BE_URI="http://127.0.0.1:$dead_port" \
  PLATFORM_GATEWAY_CARELOG_RAG_URI="http://127.0.0.1:$dead_port" \
  PLATFORM_GATEWAY_CARELOG_REGISTRATION_ENABLED=true \
  PLATFORM_GATEWAY_JWT_VERIFICATION_SECRET="$gateway_legacy_jwt_key" \
  PLATFORM_GATEWAY_INTERNAL_SECRET="$gateway_internal_secret" \
  PLATFORM_GATEWAY_FINANCE_TRUSTED_CONTEXT_ENABLED=false \
  PLATFORM_GATEWAY_FINANCE_TRUSTED_CONTEXT_SERVICE_SECRET="$finance_trusted_context_proof" \
  PLATFORM_GATEWAY_FINANCE_REFERENCE_ENABLED=false \
  "$JAVA_HOME_17_X64/bin/java" -jar "$gateway_jar" >"$log_dir/gateway.log" 2>&1 &
gateway_pid=$!

wait_ready "http://127.0.0.1:$gateway_management_port/actuator/health/readiness" Gateway

E2E_GATEWAY_URL="http://127.0.0.1:$gateway_port" \
E2E_CARELOG_URL="http://127.0.0.1:$carelog_port" \
E2E_CARELOG_ACCESS_LOG_DIR="$carelog_access_dir" \
node "$verifier"
