# Carelog V1 Container Foundation

## Runtime boundary

The production Compose file runs only `carelog-be`. Build the image from the repository root with `carelog-be/Dockerfile`; Compose consumes a prebuilt image selected by `CARELOG_IMAGE`. The legacy `carelog-gateway` project is not packaged. V1 ingress remains `platform-services/gateway-app`.

The application listens on container port `8080`. Bind the host port to the Carelog EC2 private interface and allow inbound TCP `8080` only from the shared Gateway security group. Do not add an Internet-wide rule. Docker publishes the management port as `127.0.0.1:19090` on the EC2 host; do not add it to an EC2 security group. This host bind does not isolate the listener from other containers attached to the same Docker network. Keep untrusted workloads off that network and validate host-local scraping separately. A host-local Alloy process can scrape `http://127.0.0.1:19090/actuator/prometheus`.

RDS and Valkey are external services and are not Compose services. Permit Carelog EC2 egress to the selected RDS endpoint on TCP `5432` and Valkey private endpoint on TCP `6379`. RDS must have public access disabled. Valkey must not be reachable from the public Internet. Configure private DNS or private IP routing and security group rules; Docker bridge networks are not shared with Gateway or other EC2 hosts.

## Configuration and secret files

Copy `.env.example` to an untracked `.env`, replace the private addresses and application Origin, and use an immutable image reference in production (for example, a registry image digest). The sample contains no credentials. `POSTGRES_PASSWORD` is used only by the legacy local dependency Compose file.

Set `CARELOG_SECRET_DIR` to a directory readable by container UID/GID `10001`; it is mounted read-only at `/run/secrets` and imported as a Spring config tree. Provide files using the exact Spring property names:

| File name | Purpose |
| --- | --- |
| `spring.datasource.username` | RDS runtime role |
| `spring.datasource.password` | RDS credential |
| `jwt.secret-key` | Existing Carelog JWT signing key; preserve the existing contract |
| `gateway.internal-secret` | Shared Gateway-to-Carelog secret |
| `spring.data.redis.username` | Optional Valkey ACL username |
| `spring.data.redis.password` | Optional Valkey ACL password |
| `carelog.auth.oauth.kakao.client-id` | Existing Kakao OAuth client id, when configured |
| `carelog.auth.oauth.kakao.client-secret` | Existing Kakao OAuth client secret, when configured |

When Kakao OAuth is enabled, also provide the existing URI, timeout and `oauth.redirect-uris.kakao.WEB` / `oauth.redirect-uris.kakao.MOBILE` properties through the same config tree. The existing OAuth conditional still requires its current complete configuration. `carelog.internal.identity-claims.enabled` remains false; this deployment does not enable Identity cutover.

Mount the current AWS RDS CA bundle at `CARELOG_RDS_CA_FILE`; the container exposes it as `/run/rds-ca.pem`. PgJDBC uses `sslmode=verify-full` and that CA file. Set `SPRING_DATASOURCE_URL` to the actual RDS hostname, database and port so hostname validation remains enabled. Do not put RDS credentials in the JDBC URL.

Set `JWT_ACCESS_TOKEN_VALIDITY` and `JWT_REFRESH_TOKEN_VALIDITY` to the existing Carelog values in milliseconds. The sample values in `.env.example` are illustrative only; preserve the currently approved token lifetime policy.

Valkey TLS defaults to enabled. If the selected server does not support the configured TLS and ACL policy, stop and resolve that deployment contract rather than weakening the setting silently. Supply ACL files only when ACL is configured on the selected Valkey server.

## HTTP and Gateway boundary

Production CORS requires the exact `CARELOG_CORS_ALLOWED_ORIGINS` allowlist and does not allow credentials. Production Swagger UI and API Docs are disabled and denied by the application security chain. The existing localhost origins and Swagger convenience remain in non-production profiles. CORS preflight `OPTIONS` requests retain the existing filter exemption; all other direct application requests require the existing `X-Gateway-Secret`. Restrict private TCP `8080` ingress to the shared Gateway security group and do not accept client-supplied trusted identity headers at the Gateway.

## Authentication and state contracts

The existing `GatewayHeaderAuthFilter`, JWT, OAuth and ownership code remains in place. Direct application requests still require the shared Gateway secret. The current shared Gateway removes client supplied `X-User-Id`, `X-Organization-Id`, `X-Role`, `X-Public-Id` and `X-Gateway-Secret` before it verifies the bearer token and injects trusted values. Carelog's EC2 security group is the network boundary for direct ingress; the secret filter remains the application boundary.

The shared Gateway and Carelog must connect to the same Valkey keyspace for JWT revocation: both use `blacklist:<raw access token>`. Carelog's OAuth state store uses `oauth:state:<state>`, stores it with TTL and consumes it atomically with `GETDEL`. Use a Valkey release supporting `GETDEL` (Valkey 7.2 or newer), then verify `SET`+TTL, GETDEL and blacklist visibility against the actual shared deployment. Do not rename the existing Spring Data Redis / Lettuce client or key prefix.

## Health, shutdown and metrics

Spring graceful shutdown is enabled with a 30 second shutdown phase and Compose allows 45 seconds for `SIGTERM`. The JVM sees the container memory limit and caps heap at 70% of it. Liveness checks only process state. Readiness includes database and Redis-compatible Valkey health. Health details are hidden. Only `health` and `prometheus` are exposed on the management listener; the published host port is loopback-only.

Micrometer exports Spring MVC HTTP request count and duration histogram at `/actuator/prometheus`, with status, outcome, URI template, method, `service=carelog` and `environment=production` tags. Production enables histogram buckets at 25ms, 50ms, 100ms, 250ms, 500ms, 1s, 2.5s, 5s and 10s. `carelog.http.timeouts` counts requests whose resolved Throwable is classified as servlet async, JDBC, dependency, network or deadline timeout; it records one bounded `kind` value per request and does not include exception messages or class names.

`ranikun.db.query.duration` measures actual JDBC statement execution calls, including prepared and batched statements. Its bounded tags are SQL operation (`select`, `insert`, `update`, `delete`, `call`, `ddl`, `other`, `batch`) and outcome (`success`, `error`, `timeout`); histogram SLOs are 1ms, 5ms, 10ms, 25ms, 50ms, 100ms, 250ms, 500ms and 1s. It never exports SQL text, parameter values, users or tenants. Hikari's connection acquisition/pool metrics remain separate and are not included in statement duration. JVM, process, Hikari and Linux process RSS (`process.memory.rss`, when `/proc/self/status` is readable) are also available. Histogram buckets, counters and gauges should be confirmed from a scrape before alert rules rely on their exact exposition names.

The existing Actuator security guard and production listener split remain unchanged: only health and Prometheus are exposed on the separate management listener, and Compose binds its host port to loopback. Host loopback does not block peers on the same Docker network from reaching the container listener. Keep untrusted workloads off that network; enforce management reachability in the deployment network and verify actual AWS security groups separately. Logs identify the service and environment. The app Compose does not deploy Alloy or Grafana Cloud.

## Database migration gate

Production Compose sets `CARELOG_FLYWAY_ENABLED=false`; Hibernate remains `ddl-auto=validate`. No production migration was executed by this change. The accepted shared PostgreSQL direction is one application database with schema ownership. Carelog's current V1 baseline is unqualified and follows the existing default schema; the `public` to `carelog` schema move is not implemented here. Do not set `carelog` as the production search path until the schema migration is rehearsed and role grants are proven.

The current app passed local disposable PostgreSQL 16.15 and 18.6 TLS `verify-full` startup smoke tests. On both versions, Flyway validated and applied V1–V8 and Hibernate completed schema validation; the server certificate used a temporary local CA and matched the `carelog-pg` hostname. PostgreSQL 16 is also the version used by the JDBC metrics Testcontainers test. PostgreSQL 18.6 completed that smoke test, but Flyway logged that versions newer than PostgreSQL 17 have not been tested. Treat this as compatibility evidence, not a statement that PostgreSQL 18 is officially supported. These checks do not validate AWS RDS CA certificates, endpoint names or security groups.

Review the exact Flyway V1–V8 order against a disposable copy before production use. In particular, V5 backfills `refresh_token.account_id` and deletes rows that remain null; existing legacy refresh sessions can be removed. Decide the session continuity / forced re-login behavior, prove recovery, verify the final schema and role, then perform a separately approved migration. Do not enable startup Flyway in production before that gate is complete.

## Production gates still open

- Select the production PostgreSQL major with the Flyway PostgreSQL 18 support warning in view, then verify the actual RDS CA, endpoint hostname and private networking. Local disposable PostgreSQL/TLS smoke evidence does not verify the AWS RDS CA bundle or security groups.
- Rehearse the current public-schema baseline and the V1–V8 effects, especially legacy refresh-session deletion; establish `carelog` schema ownership and least-privilege role.
- Select the Valkey EC2 placement, private endpoint, TLS/ACL mode, secret rotation and shared Gateway/Carelog keyspace; run TTL, GETDEL and blacklist integration checks.
- Confirm the Carelog EC2 private address, Gateway-only ingress rule, RDS/Valkey security group rules and the host-local Alloy scrape path.
- Build and run the final image on the chosen EC2 architecture. The pinned Java base manifests list amd64 and arm64, but that alone does not prove the final image or application runtime on Graviton.
- Supply production secrets and Kakao OAuth configuration through the agreed external secret delivery mechanism; verify Gateway secret and JWT key coordination without exposing values.

These gates are not AWS provisioning or deployment steps; both remain outside this repository change.
