# NovaBank

> A production-grade digital banking platform built using Domain-Driven Design (DDD), Clean Architecture, and Microservices.

## Overview

NovaBank is a learning and portfolio project that demonstrates how to design and build an enterprise-grade banking platform using modern backend and frontend technologies.

The project follows industry best practices including:

- Domain-Driven Design (DDD)
- Clean Architecture
- Hexagonal Architecture (Ports & Adapters)
- Microservices
- Event-Driven Design
- RESTful APIs
- JWT-based Authentication
- Test-Driven Development (TDD)
- CI/CD-ready project structure

The goal is not only to build a banking application, but to demonstrate production-quality software engineering practices that are commonly used in enterprise environments.

---

## Architecture

Six Spring Boot services, each with its own PostgreSQL database, talking through REST (clients)
and Kafka (service to service).

```
                 Angular web (frontend/novabank-web)
                               │ REST + JWT
     ┌──────────┬──────────────┼──────────────┬──────────────┐
     ▼          ▼              ▼              ▼              ▼
 ┌────────┐ ┌──────────┐ ┌──────────┐   ┌──────────┐   ┌──────────┐
 │  Auth  │ │ Customer │ │ Account  │   │  Ledger  │   │ Payment  │
 │  8081  │ │   8082   │ │   8083   │   │   8084   │   │   8085   │
 └───┬────┘ └────┬─────┘ └────┬─────┘   └────┬─────┘   └────┬─────┘
     │           │            │              │              │
     └───────────┴────────────┴──── Kafka ───┴──────────────┘
          events + commands through each service's transactional outbox
                                   │ every business event
                                   ▼
                           ┌───────────────┐
                           │ Audit   8086  │  append-only, hash-chained
                           └───────────────┘
```

| Service | Responsibility |
|---|---|
| Auth | Registration, email verification, login, RS256 access tokens, JWKS |
| Customer | Customer profiles, KYC review and approval |
| Account | Accounts and account numbers, funds holds, balance projection |
| Ledger | Double-entry, immutable financial source of truth |
| Payment | Transfers and deposits as an orchestrated saga |
| Audit | Append-only, HMAC-chained log of every business event; integrity check |

Details: [service contracts](docs/service-contracts.md), [event catalog](docs/event-catalog.md),
[payment lifecycle](docs/payment-lifecycle.md), ADRs in `docs/architecture-decisions` (index in [decisions](docs/decisions.md)).

---

## Repository Structure

```
novabank-platform/
├── libs/novabank-platform-starter/   # Shared technical building blocks (outbox, idempotency,
│                                     # security, RFC 9457 errors, Money); no domain code
├── services/                         # novabank-{auth,customer,account,ledger,payment}-service
├── frontend/novabank-web/            # Angular frontend
├── scripts/e2e/money-flow.sh         # End-to-end check across all services
├── docs/                             # ADRs, contracts, standards
├── compose.yaml                      # Local PostgreSQL databases, Kafka, Mailpit
└── pom.xml                           # Parent Maven project
```

---

## Technology Stack

- **Backend:** Java 25, Spring Boot 4.1, Spring Security (OAuth2 resource server, RS256 JWT),
  Spring Data JPA / JDBC, Flyway, PostgreSQL 18, Apache Kafka 4 (KRaft)
- **Testing:** JUnit 6, Mockito, AssertJ, Testcontainers (PostgreSQL, Kafka, Mailpit), JaCoCo
- **Build:** Maven (wrapper), Spotless with google-java-format
- **Frontend:** Angular, TypeScript, RxJS
- **Local infrastructure:** Docker Compose, Mailpit (catches all email)

---

## Current Features

- Registration with email verification; login with short-lived RS256 access tokens
- Customer profiles and KYC approval by compliance officers
- Accounts with check-digit account numbers; `Idempotency-Key` on account opening
- Double-entry ledger: balanced and immutable, enforced in code and by the database;
  reconciliation endpoint
- Transfers and operations deposits as a saga: funds holds, compensation, timeouts with safe
  re-sends, exactly-once effects despite at-least-once messaging
- RFC 9457 error responses, transactional outbox, idempotent consumers, dead-letter topics

---

## Run locally

1. **Prerequisites:** JDK 25, Docker.
2. **Configuration:** copy `.env.example` to `.env` and set the passwords (it is git-ignored).
3. **Infrastructure:**

   ```bash
   docker compose up -d --wait
   ```

   PostgreSQL per service (ports 5433–5437), Kafka on 9092, Mailpit at http://localhost:8025.

4. **Services:** in five terminals, from the repository root:

   ```bash
   cd services/novabank-auth-service && ../../mvnw spring-boot:run -Dspring-boot.run.profiles=local
   # same for customer, account, ledger, payment and audit
   ```

   The `local` profile reads `.env`, uses the local ports and generates a throwaway JWT key pair.
   It also logs plain text and samples every trace; without it, logs are ECS JSON (one object
   per line, with `traceId`/`spanId`) and 10% of traces are sampled.

   **Probes and metrics:** each service serves Actuator on its own management port, main port
   plus 1000 (9081–9085, override with `MANAGEMENT_PORT`), for example
   `http://localhost:9081/actuator/prometheus`. Liveness and readiness are also on the main
   port as `/livez` and `/readyz`. To export traces, set
   `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` to an OTLP/HTTP collector URL.

   **API docs:** with the `local` profile each service serves Swagger UI at
   `http://localhost:<port>/swagger-ui.html` (OpenAPI at `/v3/api-docs`). Everywhere else both
   are off unless `API_DOCS_ENABLED=true`.

   **Sessions (auth):** sign-in returns a 15-minute access token and sets a refresh token as an
   HttpOnly, Secure, SameSite=Strict cookie. `POST /api/v1/auth/refresh` (with the
   `X-Requested-With` header) rotates it; a reused token ends the whole sign-in. A session ends
   after 30 minutes without a refresh or 8 hours after sign-in (`security.refresh-token.*`).

   **Sign-in protection (auth):** five wrong passwords within 15 minutes lock an account for 15
   minutes (`security.login.*`). Login, registration and the verify-email endpoints are limited
   per client IP (`security.rate-limit.*`, roomier in the `local` profile) and answer 429 with
   `Retry-After` when exceeded.

5. **End-to-end check** (needs Python 3):

   ```bash
   scripts/e2e/money-flow.sh
   ```

   Registers and verifies users through Mailpit, approves KYC, opens two accounts, deposits,
   transfers, checks that an overspend is refused and that the ledger reconciles. Staff roles are
   granted through auth's admin API by `admin@novabank.local`, the bootstrap administrator in the
   `local` profile and compose (`BOOTSTRAP_ADMIN_EMAIL`); the script registers it on its first run.
   CI runs the same script on every PR against the compose stack, built from the tested jars.

**Build and test everything** (unit tests, Testcontainers integration tests, formatting):

```bash
./mvnw -B clean verify
```

**Container images:** one `Dockerfile` at the repository root builds any service (pass the
module and its two ports):

```bash
docker build --build-arg SERVICE=novabank-auth-service \
  --build-arg SERVER_PORT=8081 --build-arg MANAGEMENT_PORT=9081 -t novabank/auth-service .
```

The image runs the layered jar on a JRE as non-root user `10001`, in UTC, with the heap at 75%
of the container memory limit (`JAVA_TOOL_OPTIONS` for extra JVM flags). Its `HEALTHCHECK`
calls `/readyz`. Configuration comes from environment variables, as in `.env.example`.

**Whole stack in containers** (instead of step 4): the `apps` profile adds the five services,
Prometheus and Jaeger to the infrastructure. It needs about 4 GB of memory for Docker.

```bash
docker compose --profile apps up -d --build --wait
scripts/e2e/money-flow.sh        # same end-to-end check
docker compose --profile apps down   # keeps the data volumes
```

The services run with their production configuration (ECS JSON logs, read-only filesystem, no
Linux capabilities). Only the sampling (every trace), the JWT key (throwaway) and the per-IP
limits (roomier, since all requests come from the Docker gateway) differ. Prometheus:
http://localhost:9090, traces in Jaeger: http://localhost:16686.

---

## Documentation

Project documentation is maintained under the `docs` directory.

Documentation includes:

- Architecture Decision Records (`docs/architecture-decisions`, indexed in `docs/decisions.md`)
- [Service contracts](docs/service-contracts.md), [event catalog](docs/event-catalog.md),
  [payment lifecycle](docs/payment-lifecycle.md)
- Coding and engineering standards
- [Platform roadmap](docs/platform-roadmap.md)

---

## Development Principles

NovaBank follows several engineering principles:

- Single Responsibility Principle
- SOLID Principles
- Domain-Driven Design
- Clean Architecture
- Hexagonal Architecture
- Convention over Configuration
- Testability
- Separation of Concerns

---

## Branching Strategy

Feature development follows a feature branch workflow.

Example:

```
feature/NVB-027-login-page
feature/NVB-030-auth-guard
feature/NVB-032-dashboard-shell
```

Each feature is developed through small, reviewable pull requests.

---

## Roadmap

The project is being implemented incrementally.

Current areas of development include:

- Identity & Authentication
- Customer Management
- Account Management
- Payments
- Transaction History
- Notifications
- Observability
- Deployment & CI/CD

---

## Contributing

Please read the project's `CONTRIBUTING.md` before submitting changes.

---

## License

This project is licensed under the MIT License.
