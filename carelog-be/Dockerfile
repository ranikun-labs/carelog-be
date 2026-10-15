# syntax=docker/dockerfile:1.7

# Digest-pinned Java 17 base indexes include linux/amd64 and linux/arm64.
ARG BUILD_IMAGE=eclipse-temurin:17-jdk-noble@sha256:fb79620c3f6ee8f1c7530a8dc4fd49e3b410bcef037694ad4858a1553bac4170
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-noble@sha256:900322f4cc1b9da9d730ae4bb56d8a1dabbaa9cfa32809fc8b542b600370a045
ARG HEALTHCHECK_IMAGE=busybox:1.37.0-musl@sha256:5cec3fc171c87218698e85a52af7087de727372aae264a787b8112901a5b0092

FROM ${BUILD_IMAGE} AS build

RUN groupadd --system --gid 10001 carelog \
    && useradd --system --uid 10001 --gid carelog --create-home --home-dir /home/carelog carelog \
    && install -d --owner=carelog --group=carelog /home/carelog/.gradle

WORKDIR /workspace
RUN chown carelog:carelog /workspace
COPY --chown=carelog:carelog . .
USER 10001:10001
ENV GRADLE_USER_HOME=/home/carelog/.gradle

# The proxy CA is a BuildKit-only input. A temporary truststore is removed in
# this same layer and the complete builder stage is excluded from the runtime image.
RUN --mount=type=cache,target=/home/carelog/.gradle,uid=10001,gid=10001 \
    --mount=type=secret,id=proxy_ca,required=false,uid=10001,gid=10001,mode=0400 \
    set -eu; \
    proxy_url="${HTTPS_PROXY:-${https_proxy:-${HTTP_PROXY:-${http_proxy:-}}}}"; \
    if [ -n "$proxy_url" ]; then \
        proxy_authority="${proxy_url#*://}"; \
        proxy_authority="${proxy_authority%%/*}"; \
        proxy_authority="${proxy_authority##*@}"; \
        case "$proxy_authority" in \
            *:*) proxy_host="${proxy_authority%:*}"; proxy_port="${proxy_authority##*:}" ;; \
            *) proxy_host="$proxy_authority"; proxy_port=80 ;; \
        esac; \
        export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttps.proxyHost=$proxy_host -Dhttps.proxyPort=$proxy_port -Dhttp.proxyHost=$proxy_host -Dhttp.proxyPort=$proxy_port"; \
    fi; \
    if [ -f /run/secrets/proxy_ca ]; then \
        cp "$JAVA_HOME/lib/security/cacerts" /tmp/carelog-cacerts; \
        keytool -importcert -noprompt -trustcacerts -alias session-proxy-ca \
            -file /run/secrets/proxy_ca -keystore /tmp/carelog-cacerts -storepass changeit; \
        export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/tmp/carelog-cacerts -Djavax.net.ssl.trustStorePassword=changeit"; \
    fi; \
    ./gradlew --no-daemon --no-configuration-cache :carelog-be:bootJar --stacktrace; \
    rm -f /tmp/carelog-cacerts

FROM ${HEALTHCHECK_IMAGE} AS healthcheck

FROM ${RUNTIME_IMAGE} AS runtime

RUN groupadd --system --gid 10001 carelog \
    && useradd --system --uid 10001 --gid carelog --no-create-home --home-dir /app carelog

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/carelog-be/build/libs/carelog.jar /app/app.jar
COPY --from=healthcheck --chown=0:0 /bin/busybox /usr/local/bin/busybox

USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=20.0 -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError" \
    TZ=UTC

EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD ["/usr/local/bin/busybox", "wget", "-q", "-Y", "off", "--spider", "http://127.0.0.1:8081/actuator/health/readiness"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
