# syntax=docker/dockerfile:1
#
# The NovaBank web app (8.6b): built with Node, served by unprivileged nginx with the API on the
# same origin (Decision AP). Build from this directory:
#   docker build -t novabank/web .
# Base images are written out in each FROM so Dependabot can update them.

# --- 1. Build ----------------------------------------------------------------------------------
FROM node:26.13.1-alpine3.24 AS build
WORKDIR /app
# Dependencies first: this layer is reused until package*.json change.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# --- 2. Serve ----------------------------------------------------------------------------------
# nginx-unprivileged: runs as uid 101 and listens on 8080, no root at any point.
FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24

# Where the API lives; compose names by default, override per environment.
ENV AUTH_URL=http://auth-service:8081 \
    CUSTOMER_URL=http://customer-service:8082 \
    ACCOUNT_URL=http://account-service:8083 \
    PAYMENT_URL=http://payment-service:8085 \
    NGINX_RESOLVER=127.0.0.11

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/proxy_params.conf /etc/nginx/proxy_params.conf
COPY --from=build /app/dist/novabank-web/browser/ /usr/share/nginx/html/

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/index.html || exit 1
