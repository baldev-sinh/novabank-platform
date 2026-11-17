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

Five Spring Boot services, each with its own PostgreSQL database, talking through REST (clients)
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
```

| Service | Responsibility |
|---|---|
| Auth | Registration, email verification, login, RS256 access tokens, JWKS |
| Customer | Customer profiles, KYC review and approval |
| Account | Accounts and account numbers, funds holds, balance projection |
| Ledger | Double-entry, immutable financial source of truth |
| Payment | Transfers and deposits as an orchestrated saga |

Details: [service contracts](docs/service-contracts.md), [event catalog](docs/event-catalog.md),
[payment lifecycle](docs/payment-lifecycle.md), ADRs in `docs/architecture-decisions` and `docs/adr`.

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
   # same for customer, account, ledger and payment
   ```

   The `local` profile reads `.env`, uses the local ports and generates a throwaway JWT key pair.
   It also logs plain text and samples every trace; without it, logs are ECS JSON (one object
   per line, with `traceId`/`spanId`) and 10% of traces are sampled.

   **Probes and metrics:** each service serves Actuator on its own management port, main port
   plus 1000 (9081–9085, override with `MANAGEMENT_PORT`), for example
   `http://localhost:9081/actuator/prometheus`. Liveness and readiness are also on the main
   port as `/livez` and `/readyz`. To export traces, set
   `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` to an OTLP/HTTP collector URL.

   **Sign-in protection (auth):** five wrong passwords within 15 minutes lock an account for 15
   minutes (`security.login.*`). Login, registration and the verify-email endpoints are limited
   per client IP (`security.rate-limit.*`, roomier in the `local` profile) and answer 429 with
   `Retry-After` when exceeded.

5. **End-to-end check** (needs Python 3):

   ```bash
   scripts/e2e/money-flow.sh
   ```

   Registers and verifies users through Mailpit, approves KYC, opens two accounts, deposits,
   transfers, checks that an overspend is refused and that the ledger reconciles. Local only: it
   grants staff roles directly in the auth database (there is no staff provisioning API yet).

**Build and test everything** (unit tests, Testcontainers integration tests, formatting):

```bash
./mvnw -B clean verify
```

---

## Documentation

Project documentation is maintained under the `docs` directory.

Documentation includes:

- Architecture Decision Records (`docs/adr`, `docs/architecture-decisions`)
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
