# NovaBank Naming Conventions

This document defines the naming conventions used throughout the NovaBank platform. Consistent naming improves readability, maintainability, and communication across the codebase.

---

# Java Packages

* Use lowercase.
* Organize packages by **business capability** or **architectural responsibility**.
* Avoid package names based on Java language constructs (e.g. `entity`, `dto`, `util`).

**Examples**

```text
com.novabank.auth
com.novabank.customer
com.novabank.account
com.novabank.payment
com.novabank.ledger
```

---

# Classes

* Use **PascalCase**.
* Choose names that represent business concepts.

**Examples**

* `User`
* `Account`
* `Payment`
* `LedgerTransaction`
* `FundsHold`

---

# Interfaces

* Use **PascalCase**.
* Do **not** prefix interfaces with `I`.

**Good**

* `UserRepository`
* `RegisterUserUseCase`
* `PaymentGateway`

**Avoid**

* `IUserRepository`
* `IPaymentGateway`

---

# Aggregates

Aggregate Roots should use singular business names.

**Examples**

* `User`
* `Account`
* `Payment`

---

# Value Objects

Value Objects should represent immutable business concepts.

**Examples**

* `UserId`
* `AccountId`
* `PaymentId`
* `EmailAddress`
* `PasswordHash`
* `Money`

---

# Repository Ports

Repository interfaces should express the aggregate they manage.

**Examples**

* `UserRepository`
* `AccountRepository`
* `PaymentRepository`

Avoid technology-specific names such as:

* `JpaUserRepository`
* `PostgresRepository`

These belong in the Infrastructure layer.

---

# Application Services

Prefer business-oriented names.

**Examples**

* `RegisterUserService`
* `AuthenticateUserService`
* `TransferFundsService`

---

# Use Cases

Use case interfaces should clearly express the business capability.

**Examples**

* `RegisterUserUseCase`
* `AuthenticateUserUseCase`
* `TransferFundsUseCase`
* `LockUserUseCase`

---

# Commands

Use the `Command` suffix.

Commands represent input to an application use case.

**Examples**

* `RegisterUserCommand`
* `TransferFundsCommand`
* `LockUserCommand`

---

# Queries

Use the `Query` suffix.

Queries represent read operations.

**Examples**

* `FindUserByIdQuery`
* `FindAccountByNumberQuery`
* `FindPaymentByReferenceQuery`

---

# Responses

Use the `Response` suffix.

Responses represent output from an application use case.

**Examples**

* `RegisterUserResponse`
* `PaymentResponse`
* `AccountResponse`

---

# REST Controllers

Use the `Controller` suffix.

**Examples**

* `UserController`
* `PaymentController`
* `AccountController`

---

# REST APIs

* Use plural resource names.
* Use lowercase.
* Version all public APIs.

**Examples**

```text
/api/v1/users
/api/v1/accounts
/api/v1/payments
```

Avoid verbs in resource paths.

**Avoid**

```text
/api/v1/createUser
/api/v1/makePayment
```

---

# Domain Events

Use business events in the past tense.

**Examples**

* `UserRegistered`
* `PaymentInitiated`
* `FundsHeld`
* `PaymentCompleted`
* `LedgerTransactionPosted`

Avoid command-style names.

**Avoid**

* `CreatePayment`
* `RegisterUser`
* `HoldFunds`

---

# Kafka Topics

Use lowercase with dot notation.

**Examples**

```text
auth.user.registered
payment.completed
ledger.transaction.posted
account.balance.updated
```

---

# Database Tables

Use lowercase snake_case.

**Examples**

```text
users
roles
payments
ledger_transactions
ledger_entries
account_holds
```

---

# Database Columns

Use lowercase snake_case.

**Examples**

```text
created_at
updated_at
customer_id
account_number
payment_id
```

---

# Exceptions

Use the `Exception` suffix.

Name exceptions according to the violated business rule.

**Examples**

* `DuplicateEmailException`
* `InsufficientFundsException`
* `PaymentFailedException`
* `UserLockedException`

---

# Enums

Use singular enum type names.

Enum constants should use `UPPER_SNAKE_CASE`.

**Examples**

```java
UserStatus

ACTIVE
LOCKED
DISABLED
PENDING_VERIFICATION
```

---

# Constants

Use `UPPER_SNAKE_CASE`.

**Examples**

```java
MAX_LOGIN_ATTEMPTS

DEFAULT_PAGE_SIZE

JWT_EXPIRATION_MINUTES
```

---

# Branch Names

Use descriptive lowercase names.

**Examples**

```text
feature/auth-domain

feature/user-registration

bugfix/email-validation

hotfix/payment-timeout
```

---

# Git Commit Messages

Follow the Conventional Commits specification.

**Examples**

```text
feat(auth-domain): implement user aggregate

feat(auth-application): add register user service

fix(auth-domain): prevent duplicate role assignment

refactor(auth-domain): simplify user lifecycle

test(auth-domain): add user aggregate tests

docs(architecture): add ADR-005
```

---

# Documentation

Use consistent naming for project documentation.

**Examples**

```text
README.md

coding-standards.md

naming-conventions.md

architecture-principles.md

decisions.md
```

Architecture Decision Records should follow the format:

```text
ADR-001-domain-driven-design.md

ADR-002-value-objects.md

ADR-003-rich-domain-model.md
```

---

# General Principles

* Prefer business terminology over technical jargon.
* Choose names that clearly express intent.
* Be consistent across all services.
* Avoid abbreviations unless they are universally understood.
* Use singular names for aggregates, entities, and Value Objects.
* Favor descriptive names over short or ambiguous ones.
* Keep naming aligned with the ubiquitous language of the business domain.
