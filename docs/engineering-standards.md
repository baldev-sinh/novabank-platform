# NovaBank Engineering Standards

This document defines the engineering standards adopted across the NovaBank platform. All services should adhere to these standards to ensure consistency, maintainability, and scalability.

---

# Java

* **Java Version:** Java 21
* Prefer modern Java language features where appropriate.
* Use records for immutable Value Objects.
* Avoid unnecessary inheritance; prefer composition.

---

# Spring Boot

* **Version:** Spring Boot 3.5.x
* Constructor injection only.
* No field injection.
* Keep configuration externalized.
* Do not place Spring annotations in the Domain layer.

---

# Architecture

NovaBank follows:

* Domain-Driven Design (DDD)
* Clean Architecture
* Hexagonal Architecture (Ports & Adapters)
* Rich Domain Model

Business rules belong in the Domain layer.

Dependencies always point toward the Domain.

---

# Database

* **Database:** PostgreSQL
* One database per microservice.
* No cross-service database access.
* Use UUIDs for aggregate identifiers.
* Use UTC timestamps (`Instant`) for persisted dates.
* Database schema owned exclusively by its service.

---

# Build Tool

* **Maven**
* Use the Maven Wrapper (`mvnw`) for builds.
* Keep dependencies up to date.
* Do not commit generated artifacts.

---

# Source Control

* **Git**
* Use Pull Requests for all changes.
* Squash or rebase commits before merging when appropriate.
* Protect the `main` branch.

---

# Branching Strategy

## Main Branch

```text
main
```

## Feature Branches

```text
feature/<feature-name>
```

Examples:

```text
feature/user-registration
feature/auth-domain
feature/payment-service
```

## Bug Fix Branches

```text
bugfix/<issue-name>
```

Example:

```text
bugfix/email-validation
```

## Hotfix Branches

```text
hotfix/<issue-name>
```

---

# Commit Convention

Follow the Conventional Commits specification.

Examples:

```text
feat(auth-domain): implement user aggregate

feat(auth-application): add register user service

fix(auth-domain): prevent duplicate role assignment

refactor(auth-domain): simplify user lifecycle

docs(architecture): add ADR-005

test(auth-domain): add user aggregate tests
```

---

# API Standards

* All REST APIs must be versioned.
* Use nouns rather than verbs in resource paths.
* Return appropriate HTTP status codes.
* Follow consistent request and response structures.
* Validate all incoming requests.

Examples:

```text
/api/v1/accounts

/api/v1/customers

/api/v1/payments
```

---

# Security Standards

* JWT-based authentication.
* OAuth2/OpenID Connect for authorization where applicable.
* HTTPS only.
* Never expose sensitive information in logs.
* Store secrets outside source control.
* Validate all external input.

---

# Logging Standards

* Use structured JSON logging.
* Include a Correlation ID for every request.
* Log business events at appropriate levels.
* Never log passwords, password hashes, tokens, or secrets.

---

# Event Standards

All domain events should contain:

* `eventId`
* `eventType`
* `eventTimestamp`
* `correlationId`
* `payload`

Events should be immutable and versioned when necessary.

---

# Testing Standards

Every service should include:

* Unit Tests
* Integration Tests
* Testcontainers (where infrastructure is involved)
* Contract Tests (for service integration)

Testing principles:

* Use JUnit 5.
* Use AssertJ for assertions.
* Use Mockito for mocking.
* Test business behavior rather than implementation details.

---

# Documentation Standards

Every service should include:

* `README.md`
* Architecture Decision Records (ADRs)
* API documentation
* Event documentation (if applicable)
* Coding standards
* Architecture documentation

Documentation should be updated alongside code changes.

---

# Domain Standards

* Business logic belongs inside aggregates.
* No public setters on domain entities.
* Value Objects must be immutable.
* Repository interfaces belong to the Domain layer.
* Infrastructure implements Domain ports.
* The Domain layer must remain framework-independent.

---

# Code Quality Standards

* Follow SOLID principles.
* Prefer expressive method names.
* Keep methods focused on a single responsibility.
* Use meaningful exception messages.
* Avoid premature optimization.
* Eliminate dead code and unused dependencies.
* Maintain consistent formatting and naming conventions.

---

# Quality Gates

Before merging a Pull Request:

* All unit tests pass.
* Integration tests pass.
* No compiler warnings.
* No SonarLint issues.
* Code reviewed and approved.
* Documentation updated where required.
* Build completes successfully.

---

# Guiding Principles

Every change to NovaBank should strive to be:

* Correct
* Simple
* Readable
* Testable
* Maintainable
* Secure
* Framework-independent
* Production-ready
