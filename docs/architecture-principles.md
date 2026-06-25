# NovaBank Architecture Principles

## 1. Domain-Driven Design

Business domains drive the architecture.

Technology does not.

---

## 2. Single Responsibility

Each microservice owns exactly one business capability.

---

## 3. Database per Service

No service may directly access another service's database.

Communication occurs only through APIs or events.

---

## 4. API First

Every externally accessible capability must have a well-defined API contract before implementation.

---

## 5. Event-Driven Integration

Services publish domain events after successful business transactions.

Consumers must be idempotent.

---

## 6. Source of Truth

Ledger Service is the financial source of truth.

Read models and projections may be rebuilt from ledger history.

---

## 7. Immutable Financial Records

Ledger transactions are never updated or deleted.

Corrections are performed using compensating transactions.

---

## 8. Security by Default

Authentication and authorization are mandatory for every protected endpoint.

Secrets must never be committed to source control.

---

## 9. Observability by Default

Every service must expose:

* Health
* Metrics
* Structured Logs

Every request must include a Correlation ID.

---

## 10. Backward Compatibility

Breaking API changes require a new API version.

---

## 11. Fail Fast

Invalid requests should fail immediately with meaningful error responses.

---

## 12. Idempotency

All financial operations must be safe to retry.

---

## 13. Automation First

Builds, tests and deployments should be automated.

Manual production steps should be minimized.

---

## 14. Testability

Business logic must be independently testable.

Integration tests should validate infrastructure behavior.

---

## 15. Documentation

Architecture decisions, APIs and operational procedures must be documented and version-controlled.
