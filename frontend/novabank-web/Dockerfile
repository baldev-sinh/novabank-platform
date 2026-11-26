# syntax=docker/dockerfile:1
#
# A NovaBank web app (8.6b, Decision AS): built with Node, served by unprivileged nginx with its API
# on the same origin (Decision AP). APP picks the app; the build is the same for both. From this
# directory:
#   docker build -t novabank/web .                            the customer app (the default)
#   docker build --build-arg APP=staff -t novabank/staff-web .   the staff back-office
# Base images are written out in each FROM so Dependabot can update them.

ARG APP=customer

# --- 1. Build ----------------------------------------------------------------------------------
FROM node:26.13.1-alpine3.24 AS build
ARG APP
WORKDIR /app
# Dependencies first: this layer is reused until package*.json change.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# The design system library first: the apps compile against its build (dist/ui).
RUN npm run build:ui && npx ng build "$APP"

# --- 2. Serve ----------------------------------------------------------------------------------
# nginx-unprivileged: runs as uid 101 and listens on 8080, no root at any point.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
ARG APP

# Where the APIs live; compose names by default, override per environment. Each app's config uses
# only the ones it routes to.
ENV AUTH_URL=http://auth-service:8081 \
    CUSTOMER_URL=http://customer-service:8082 \
    ACCOUNT_URL=http://account-service:8083 \
    LEDGER_URL=http://ledger-service:8084 \
    PAYMENT_URL=http://payment-service:8085 \
    AUDIT_URL=http://audit-service:8086 \
    NGINX_RESOLVER=127.0.0.11

COPY nginx/${APP}.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/proxy_params.conf /etc/nginx/proxy_params.conf
COPY --from=build /app/dist/${APP}/browser/ /usr/share/nginx/html/

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/index.html || exit 1
