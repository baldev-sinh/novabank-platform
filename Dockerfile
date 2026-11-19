# syntax=docker/dockerfile:1
#
# One image recipe for every service (Decision W). Build from the repository root:
#   docker build --build-arg SERVICE=novabank-auth-service \
#     --build-arg SERVER_PORT=8081 --build-arg MANAGEMENT_PORT=9081 -t novabank/auth-service .
# Layout follows https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html

# Base images are written out in each FROM (not via ARG) so Dependabot can update them (4.6).

# Where the jar comes from: "source" builds it here (local, compose); "prebuilt" takes the jar CI
# already built and tested, passed as the named build context "jars" (build once, ship that jar).
ARG JAR_SOURCE=source

# --- 1. Build the service jar (and the shared starter it depends on) -------------------------
# Official Maven image with the same Maven as .mvn/wrapper (3.9.16): on CI runners Maven Central
# repeatedly answered 404 for the wrapper's Maven zip, so the image build doesn't download Maven.
# Keep this tag in step with maven-wrapper.properties.
FROM maven:3.9.16-eclipse-temurin-25-noble AS jar-source
ARG SERVICE
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY libs/ libs/
COPY services/ services/
# The cache mount keeps ~/.m2 between builds without putting it in any image layer.
# Tests, Spotless and JaCoCo already ran in `mvnw verify` (CI); the image only packages.
RUN --mount=type=cache,target=/root/.m2 \
    test -n "${SERVICE}" || { echo "Pass --build-arg SERVICE=<module>" >&2; exit 1; } \
    && mvn -B -q -pl "services/${SERVICE}" -am package -DskipTests \
    && cp "services/${SERVICE}/target/${SERVICE}-"*.jar /workspace/application.jar

# --- 1b. Or take the jar CI built: --build-context jars=<dir with <module>.jar> ----------------
FROM scratch AS jar-prebuilt
ARG SERVICE
COPY --from=jars ${SERVICE}.jar /workspace/application.jar

FROM jar-${JAR_SOURCE} AS jar

# --- 2. Split the jar into layers that change at different rates -----------------------------
FROM eclipse-temurin:25.0.5_8-jre-noble AS extract
WORKDIR /builder
COPY --from=jar /workspace/application.jar application.jar
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
