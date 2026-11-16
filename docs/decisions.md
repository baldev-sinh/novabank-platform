# Architectural Decisions

This document summarizes the key architectural and technical decisions made throughout the NovaBank project. Detailed rationale for significant decisions can be found in the corresponding Architecture Decision Records (ADRs).

| Area                 | Decision                                                                  |
| -------------------- | ------------------------------------------------------------------------- |
| Architecture         | Clean Architecture with Hexagonal Architecture (Ports & Adapters).        |
| Design Approach      | Domain-Driven Design (DDD).                                               |
| Domain Model         | Rich Domain Model with business logic inside aggregates.                  |
| Java Version         | Java 25 (current LTS).                                                    |
| Framework            | Spring Boot 4.1.                                                          |
| Build Tool           | Maven.                                                                    |
| Database             | PostgreSQL.                                                               |
| Persistence          | Spring Data JPA or JDBC (JdbcClient) in the Infrastructure layer only.     |
| Messaging            | Kafka with a transactional outbox and idempotent consumers.               |
| Money                | `Money` value object: BigDecimal + ISO 4217, decimal strings in JSON.     |
| Dependency Injection | Constructor injection only.                                               |
| Domain Layer         | Framework-independent (no Spring, JPA, or Lombok).                        |
| Value Objects        | Implemented as Java Records where appropriate.                            |
| Equality             | Entities compare by identity; Value Objects compare by value.             |
| Repositories         | Defined as domain interfaces and implemented in the Infrastructure layer. |
| Aggregate Root       | `User` is the Aggregate Root of the Auth domain.                          |
| Collections          | Defensive copies returned from aggregates.                                |
| Time Handling        | Domain uses `Instant` for timestamps.                                     |
| Testing              | JUnit 5, AssertJ, Mockito.                                                |
| Documentation        | Architecture documented using ADRs and Markdown.                          |
| Source Control       | Conventional Commits.                                                     |
| Branching            | Feature branches with Pull Request workflow.                              |
| Package Organization | Organized by business capability and architectural responsibility.        |
| API Design           | Application layer exposes use cases through commands and responses.       |

---

# Current ADRs

| ADR     | Title                              |
| ------- | ---------------------------------- |
| ADR-001 | Domain-Driven Design               |
| ADR-002 | Value Objects                      |
| ADR-003 | Entity and Aggregate Design        |
| ADR-004 | Rich Domain Model                  |
| ADR-005 | Layered and Hexagonal Architecture |

Core banking decisions are in `docs/architecture-decisions` (a second ADR series; the two series
will be merged, roadmap 5.3):

| ADR     | Title                                         |
| ------- | --------------------------------------------- |
| ADR-001 | Ledger as Source of Truth                     |
| ADR-002 | Payment Saga Pattern (orchestration)          |
| ADR-003 | Idempotency Strategy                          |
| ADR-004 | Identifier Strategy                           |
| ADR-005 | Compensation Strategy                         |
| ADR-006 | Funds Hold Strategy                           |
| ADR-007 | Account Balance Projection                    |
| ADR-008 | Saga Transport via Kafka Commands and Replies |
| ADR-009 | Money Representation                          |
| ADR-010 | Shared Platform Starter                       |
| ADR-011 | Funding Accounts via Operations Deposits      |

---

# Guiding Principles

* Business rules belong in the Domain layer.
* The Domain layer must remain framework-independent.
* Application services orchestrate use cases but do not contain business rules.
* Infrastructure implements ports defined by the Domain.
* Dependencies always point toward the Domain layer.
* Favor immutability wherever practical.
* Use expressive domain language in code.
* Protect business invariants through aggregates and value objects.
* Write comprehensive automated tests for business behavior.
* Keep documentation synchronized with architectural decisions.

---

This document should be updated whenever a significant architectural or technology decision is made. Each summary entry should reference a detailed ADR when applicable.
