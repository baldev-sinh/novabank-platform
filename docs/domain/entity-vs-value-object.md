# Entities vs Value Objects

## Overview

One of the fundamental concepts in Domain-Driven Design (DDD) is understanding the difference between **Entities** and **Value Objects**.

Choosing the correct modeling approach leads to a domain model that is easier to understand, maintain, and evolve.

This document explains when to use each and provides examples from the NovaBank Auth domain.

---

# What is an Entity?

An **Entity** is a domain object that has a **unique identity**.

Even if its attributes change over time, it is still considered the same object because its identity remains unchanged.

Entities usually represent business concepts that have a lifecycle.

### Characteristics

* Has a unique identifier.
* Identity is more important than attribute values.
* Mutable over its lifetime.
* May contain business behavior.
* Exists independently of its current state.

### Equality

Two entities are considered equal when they have the same identity.

```text
User A
UserId = 123

User B
UserId = 123

Result: Equal
```

Even if:

* Email changes
* Password changes
* Status changes

the entity is still the same user.

---

# NovaBank Entity Examples

## User

```java
User
```

Identity

```text
UserId
```

Attributes

* EmailAddress
* PasswordHash
* UserStatus
* Roles

The email may change.

The password may change.

The status may change.

The identity never changes.

---

## Role

```java
Role
```

Identity

```text
RoleId
```

Attributes

* RoleName

---

# What is a Value Object?

A **Value Object** represents a descriptive aspect of the domain.

It has **no identity**.

Two value objects are equal when all of their values are equal.

Value Objects should always be immutable.

### Characteristics

* No identifier.
* Immutable.
* Compared by value.
* Encapsulates validation.
* Represents a business concept.

---

# Equality

Two value objects are equal when all contained values are equal.

```text
EmailAddress("john@example.com")

equals

EmailAddress("john@example.com")
```

Result

```text
Equal
```

---

# NovaBank Value Object Examples

## UserId

Represents the unique identifier of a user.

```java
UserId
```

Although it wraps a UUID, it is modeled as a Value Object because it encapsulates the identifier value and validation.

---

## EmailAddress

Represents a validated email address.

```java
EmailAddress
```

Responsibilities

* Validation
* Normalization
* Immutability

---

## PasswordHash

Represents a hashed password.

```java
PasswordHash
```

Responsibilities

* Cannot be null
* Cannot be blank
* Immutable

---

# When Should You Create an Entity?

Create an Entity when the business cares about identity.

Examples

* User
* Role
* Customer
* Account
* Payment
* LedgerEntry
* Transaction

Ask yourself:

> "If every property changes, is it still the same business object?"

If the answer is **Yes**, it is probably an Entity.

---

# When Should You Create a Value Object?

Create a Value Object when the business cares only about the value.

Examples

* EmailAddress
* UserId
* PasswordHash
* Money
* Currency
* Address
* PhoneNumber

Ask yourself:

> "If two objects contain the same values, should they be considered equal?"

If the answer is **Yes**, it is probably a Value Object.

---

# Equality Rules

## Entity

Compare using identity.

```text
UserId
```

Two users with the same `UserId` represent the same business entity.

---

## Value Object

Compare using contained values.

```text
EmailAddress
```

Two email addresses containing the same normalized value are equal.

---

# NovaBank Examples

| Domain Object | Type         | Equality    |
| ------------- | ------------ | ----------- |
| User          | Entity       | UserId      |
| Role          | Entity       | RoleId      |
| UserId        | Value Object | UUID value  |
| EmailAddress  | Value Object | Email value |
| PasswordHash  | Value Object | Hash value  |

---

# Design Guidelines

## Prefer Value Objects

If a concept does not require identity, model it as a Value Object.

Benefits include:

* Simpler design
* Immutability
* Easier testing
* Better encapsulation
* Safer code

---

## Use Entities Only When Identity Matters

Entities should represent business concepts with a lifecycle and business behavior.

Avoid creating Entities for simple values.

---

# Summary

| Entity                       | Value Object                   |
| ---------------------------- | ------------------------------ |
| Has identity                 | No identity                    |
| Mutable                      | Immutable                      |
| Equality by identifier       | Equality by value              |
| Represents a business object | Represents a descriptive value |
| Has lifecycle                | No lifecycle                   |
| Example: User                | Example: EmailAddress          |

---

# NovaBank Domain Summary

### Entities

* User
* Role
* Customer
* Account
* Payment
* LedgerEntry

### Value Objects

* UserId
* RoleId
* EmailAddress
* PasswordHash
* Money
* Currency
* PhoneNumber
* Address

Following these principles keeps the NovaBank domain model expressive, maintainable, and aligned with Domain-Driven Design best practices.
