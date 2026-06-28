# ADR-004: Rich Domain Model

* **Status:** Accepted
* **Date:** 2026-06-29
* **Decision Makers:** NovaBank Engineering Team

---

# Context

NovaBank is designed using Domain-Driven Design (DDD) and Clean Architecture principles.

The banking domain contains complex business rules related to authentication, customer management, accounts, payments, and ledger operations. These rules must remain consistent regardless of the technology used to expose or persist the application.

To achieve this, the domain model is responsible for enforcing business rules rather than delegating them to service classes.

---

# Decision

NovaBank adopts the **Rich Domain Model** pattern.

Business rules are implemented inside domain entities, aggregates, and value objects. The domain layer owns its state and behavior and is completely independent of frameworks and infrastructure.

---

# Why NovaBank Uses Rich Domain Models

A Rich Domain Model places business behavior alongside the data it operates on.

Instead of treating domain objects as simple data containers, aggregates expose business operations that protect the consistency of the domain.

This approach provides several benefits:

* Business rules are centralized.
* Domain behavior is easier to discover.
* Invalid states become difficult or impossible to create.
* Business logic is reusable regardless of API or persistence technology.
* The domain remains easy to test without external dependencies.

---

# Why Aggregates Own Business Logic

Aggregates are responsible for maintaining business consistency.

Only an aggregate may modify its own state.

Business operations should be expressed through meaningful methods rather than by exposing mutable fields.

Examples:

* Register a new user.
* Change a password.
* Lock a user account.
* Unlock a user account.
* Assign a role.
* Remove a role.

The aggregate validates every operation before changing its internal state.

This ensures that business invariants are always preserved.

---

# Why Setters Are Forbidden

Public setters allow any caller to place an object into an invalid state.

For example, code such as:

```java
user.setEmail(null);
user.setStatus(ACTIVE);
```

bypasses business validation and makes it difficult to guarantee the correctness of the domain model.

Instead, aggregates expose business methods that clearly communicate intent, for example:

* `register(...)`
* `changePassword(...)`
* `lock()`
* `unlock()`
* `assignRole(...)`

Each business method validates its inputs and enforces domain rules before modifying state.

---

# Why Value Objects Protect Invariants

Value Objects encapsulate domain concepts that must always remain valid.

Examples include:

* `UserId`
* `EmailAddress`
* `PasswordHash`
* `RoleId`

A Value Object validates its own data during construction.

An invalid Value Object cannot exist.

For example:

* An `EmailAddress` cannot be blank.
* A `PasswordHash` cannot be null.
* A `UserId` must contain a valid UUID.

Because Value Objects are immutable, they are safe to share throughout the domain without fear of unexpected modification.

---

# Why the Domain Is Framework-Independent

The domain layer represents the business, not the implementation technology.

Domain objects must not depend on:

* Spring Framework
* Spring Security
* JPA/Hibernate
* REST controllers
* Databases
* Messaging frameworks

Keeping the domain independent provides several advantages:

* Business rules remain portable.
* Unit tests run without containers or frameworks.
* Infrastructure can evolve without affecting the domain model.
* The domain remains focused on business behavior rather than technical concerns.

Framework-specific concerns belong in the Infrastructure layer, while orchestration belongs in the Application layer.

---

# Consequences

### Positive

* Clear separation of business and infrastructure concerns.
* Strong encapsulation of business rules.
* Easier unit testing.
* Improved maintainability.
* Reduced risk of invalid domain state.
* Better alignment with Domain-Driven Design and Clean Architecture.

### Trade-offs

* Domain models require more design effort.
* Developers must understand aggregate boundaries and business invariants.
* More behavior resides inside domain objects instead of service classes.

---

# Examples from NovaBank

## Entity

* User
* Role

Entities have identity and own business behavior.

---

## Value Objects

* UserId
* RoleId
* EmailAddress
* PasswordHash

Value Objects are immutable and guarantee valid domain values.

---

## Aggregate Root

The `User` aggregate owns:

* Registration
* User lifecycle
* Password changes
* Role assignment
* Business invariants

External components interact with the `User` aggregate through business methods rather than directly modifying its internal state.

---

# Decision Summary

NovaBank adopts a Rich Domain Model to ensure that business rules remain inside the domain, aggregates protect consistency, value objects enforce invariants, and the core business logic stays independent of frameworks and infrastructure technologies.
