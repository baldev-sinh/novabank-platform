# Value Object Guidelines

## Purpose

Value Objects represent immutable business concepts that are identified solely by their values rather than by an identity.

In NovaBank, Value Objects are used to model concepts such as email addresses, identifiers, and password hashes while ensuring that business invariants are enforced at creation time.

They help create a rich, expressive, and type-safe domain model.

---

# Characteristics

A Value Object:

* Has no identity.
* Is immutable.
* Represents a business concept.
* Is compared by value.
* Cannot exist in an invalid state.
* Encapsulates validation and normalization logic.

---

# Rules

Every Value Object in NovaBank should follow these rules:

* Prefer Java `record` where appropriate.
* Be immutable.
* Validate all invariants during construction.
* Normalize values when required (for example, email addresses).
* Expose static factory methods where appropriate.
* Contain no framework annotations.
* Contain no persistence logic.
* Contain no business workflow logic.
* Provide meaningful exception messages when validation fails.
* Include comprehensive unit tests.

---

# When to Use a Value Object

Use a Value Object when:

* The concept has no independent identity.
* Equality depends only on the contained values.
* The object represents a business concept.
* Validation should occur once at creation.
* The object should be immutable.

Examples include:

* `UserId`
* `RoleId`
* `EmailAddress`
* `PasswordHash`
* `Money`
* `AccountNumber`
* `TransactionReference`

---

# When Not to Use a Value Object

Do **not** use a Value Object when:

* The object has its own lifecycle.
* The object requires a persistent identity.
* The object changes independently over time.

Such concepts should typically be modeled as **Entities** or **Aggregates**.

---

# Equality

Value Objects are compared by value.

```text
EmailAddress.of("john@example.com")
        ==
EmailAddress.of("john@example.com")
```

Two Value Objects containing the same value are considered equal, regardless of where or when they were created.

---

# Immutability

Once created, a Value Object must never change.

Any modification should result in the creation of a new instance rather than altering the existing one.

---

# Examples

Current Value Objects in the Auth domain include:

* `UserId`
* `RoleId`
* `EmailAddress`
* `PasswordHash`

Additional Value Objects planned for future domains include:

* `Money`
* `AccountNumber`
* `PaymentReference`
* `CustomerId`
* `LedgerTransactionId`

---

# Testing Requirements

Every Value Object should have unit tests covering:

* Successful creation.
* Invalid input validation.
* Null handling.
* Equality by value.
* Hash code consistency.
* String representation (where applicable).
* Normalization behavior (if applicable).

---

# Design Principles

* Keep Value Objects small and focused.
* Express business intent through meaningful names.
* Protect domain invariants.
* Favor immutability and type safety over primitive types.
* Avoid exposing raw values until necessary.
* Keep the Domain layer independent of frameworks and infrastructure.
