# syntax=docker/dockerfile:1
#
# One image recipe for every service (Decision W). Build from the repository root:
#   docker build --build-arg SERVICE=novabank-auth-service \
#     --build-arg SERVER_PORT=8081 --build-arg MANAGEMENT_PORT=9081 -t novabank/auth-service .
# Layout follows https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html

# Base images are written out in each FROM (not via ARG) so Dependabot can update them (4.6).

# --- 1. Build the service jar (and the shared starter it depends on) -------------------------
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build
ARG SERVICE
# Without unzip the Maven wrapper downloads the .tar.gz, but the pinned distributionSha256Sum is
# the .zip's, so the check fails. Build stage only; the runtime image stays minimal.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt/lists,sharing=locked \
    rm -f /etc/apt/apt.conf.d/docker-clean \
    && apt-get update && apt-get install -y --no-install-recommends unzip
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY libs/ libs/
COPY services/ services/
# The cache mount keeps ~/.m2 between builds without putting it in any image layer.
# Tests, Spotless and JaCoCo already ran in `mvnw verify` (CI); the image only packages.
# Maven Central's CDN sometimes answers 404 for the Maven zip on CI runners, so the wrapper's
# one-time download (checksum still verified) is retried before building.
RUN --mount=type=cache,target=/root/.m2 \
    test -n "${SERVICE}" || { echo "Pass --build-arg SERVICE=<module>" >&2; exit 1; } \
    && chmod +x mvnw \
    && for attempt in 1 2 3; do ./mvnw -q -v > /dev/null && break; \
         echo "Maven download failed (attempt ${attempt}), retrying" >&2; sleep 5; done \
    && ./mvnw -B -q -pl "services/${SERVICE}" -am package -DskipTests \
    && cp "services/${SERVICE}/target/${SERVICE}-"*.jar /workspace/application.jar

# --- 2. Split the jar into layers that change at different rates -----------------------------
FROM eclipse-temurin:25.0.5_8-jre-noble AS extract
WORKDIR /builder
COPY --from=build /workspace/application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# --- 3. Runtime: JRE only, non-root, read-only application files ------------------------------
FROM eclipse-temurin:25.0.5_8-jre-noble
ARG SERVER_PORT
ARG MANAGEMENT_PORT

# Required: Spring Boot reads SERVER_PORT/MANAGEMENT_PORT, so a wrong default would silently move
# the service. Each service's ports are listed in compose.yaml.
RUN test -n "${SERVER_PORT}" && test -n "${MANAGEMENT_PORT}" \
    || { echo "Pass --build-arg SERVER_PORT=<port> --build-arg MANAGEMENT_PORT=<port>" >&2; exit 1; }

# Fixed UID/GID, so Kubernetes `runAsNonRoot` / `runAsUser` and volume permissions are predictable.
RUN groupadd --system --gid 10001 novabank \
    && useradd --system --uid 10001 --gid novabank --home-dir /nonexistent --no-create-home --shell /usr/sbin/nologin novabank

WORKDIR /application
# Least to most frequently changing, so a code change only ships the last layer.
# Files stay owned by root: the app user can read but not modify its own code.
COPY --from=extract /builder/extracted/dependencies/ ./
COPY --from=extract /builder/extracted/spring-boot-loader/ ./
COPY --from=extract /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract /builder/extracted/application/ ./

ENV TZ=UTC \
    SERVER_PORT=${SERVER_PORT} \
    MANAGEMENT_PORT=${MANAGEMENT_PORT}
EXPOSE ${SERVER_PORT} ${MANAGEMENT_PORT}

USER 10001:10001

# The JRE image has no curl/wget; bash's /dev/tcp avoids adding packages. Readiness, not liveness:
# for compose `depends_on: service_healthy` means "can take traffic". Kubernetes ignores this and
# uses its own probes on /livez and /readyz.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --start-interval=2s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/${SERVER_PORT} \
      && printf "GET /readyz HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 \
      && head -n 1 <&3 | grep -q " 200 "'

# Exec form: java is PID 1 and receives SIGTERM directly (graceful shutdown, Decision V).
# Heap sized from the container memory limit; extra flags via JAVA_TOOL_OPTIONS.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=UTC", "-jar", "application.jar"]
