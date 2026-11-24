#!/usr/bin/env bash
#
# AOT cache training run for one service (Decision AM), called from the Dockerfile's runtime stage:
#   aot-training.sh <module>     e.g. aot-training.sh novabank-auth-service
#
# Spring Boot's recipe (https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html):
# start the extracted app with -XX:AOTCacheOutput and -Dspring.context.exit=onRefresh. The context
# is refreshed (every singleton is created, so their classes are loaded and recorded) and the JVM
# halts before any lifecycle bean starts: no web server, Kafka listener or scheduler ever runs.
#
# Refreshing still creates the beans that need a database at startup, and there is none in an
# image build, so this run alone turns those off: Flyway (migrations), Hibernate's schema
# validation and its JDBC metadata lookup (the dialect is named instead), and KafkaAdmin's topic
# creation. The cache only holds classes and profiles, never configuration, so the real start
# still migrates, validates and creates topics. Every value below is a placeholder: nothing is
# reachable on 127.0.0.1:1 and no key or credential ends up in the image.
set -euo pipefail

module="${1:?usage: aot-training.sh <module>}"
# novabank-auth-service -> AUTH, the prefix of its <SVC>_DB_* variables
prefix=$(sed -E 's/^novabank-(.+)-service$/\1/' <<< "${module}" | tr '[:lower:]-' '[:upper:]_')

export "${prefix}_DB_URL=jdbc:postgresql://127.0.0.1:1/aot-training"
export "${prefix}_DB_USERNAME=aot-training"
export "${prefix}_DB_PASSWORD=aot-training"
export APP_ENVIRONMENT=aot-training
export AUTH_JWT_ISSUER=http://localhost:8081
export AUTH_JWKS_URI=http://127.0.0.1:1/.well-known/jwks.json
export AUTH_TOKEN_URI=http://127.0.0.1:1/oauth2/token
export ACCOUNT_SERVICE_URL=http://127.0.0.1:1
export MAIL_HOST=127.0.0.1
export VERIFICATION_LINK_TEMPLATE='http://localhost/verify-email#token={token}'
# Throwaway keys, generated for this run and gone when it ends.
export SECURITY_JWT_GENERATE_EPHEMERAL_KEY=true
export SERVICE_GENERATE_EPHEMERAL_KEY=true
AUDIT_CHAIN_KEY=$(head -c 32 /dev/urandom | base64)
export AUDIT_CHAIN_KEY

# A failed start still writes a cache file, so the exit code is what tells success: set -e fails
# the image build instead of shipping a cache trained on a broken startup.
# G1 as in the ENTRYPOINT: the cache's archived heap objects are tied to how the GC loads them.
java -XX:+UseG1GC -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar application.jar \
  --spring.flyway.enabled=false \
  --spring.jpa.hibernate.ddl-auto=none \
  --spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
  --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
  --spring.kafka.admin.auto-create=false

test -s app.aot
