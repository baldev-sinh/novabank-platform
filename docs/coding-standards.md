# Coding Standards

These coding standards define the conventions and engineering practices followed throughout the NovaBank codebase. They are intended to promote consistency, maintainability, readability, and long-term scalability.

---

# General Principles

* Write code that clearly communicates business intent.
* Favor readability over cleverness.
* Keep methods small and focused on a single responsibility.
* Prefer composition over inheritance.
* Follow the SOLID principles.
* Apply Domain-Driven Design (DDD) and Clean Architecture consistently.

---

# Dependency Injection

* Use **constructor injection** exclusively.
* Do **not** use field injection.
* Keep dependencies immutable by declaring them `final`.
* Avoid service locators and static dependencies.

**Preferred**

```java
public RegisterUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
}
```

---

# Domain Layer

The Domain layer represents the core business model and must remain independent of frameworks.

## Rules

* No Spring annotations.
* No JPA annotations.
* No Lombok.
* No framework dependencies.
* No infrastructure concerns.
* Business logic belongs inside the domain.

---

# Value Objects

* Prefer Java `record` for immutable Value Objects.
* Validate invariants during construction.
* Value Objects must be immutable.
* Equality should be based on value.

Examples:

* `UserId`
* `RoleId`
* `EmailAddress`
* `PasswordHash`

---

# Entities and Aggregates

* Model business concepts as rich domain models.
* Do not expose public setters.
* State changes must occur through business methods.
* Equality must be based on identity.
* Aggregate Roots protect business invariants.

Examples of business methods:

* `register()`
* `activate()`
* `lock()`
* `unlock()`
* `disable()`
* `changePassword()`
* `assignRole()`

Avoid generic setters such as:

* `setStatus()`
* `setEmail()`
* `setPassword()`

---

# Naming Conventions

Use names that communicate business intent.

Prefer:

* `register()`
* `activate()`
* `assignRole()`
* `changePassword()`

Avoid:

* `create()`
* `update()`
* `process()`
* `execute()`
* `doSomething()`

Repository methods should also express intent.

Prefer:

* `findByEmail()`
* `existsByEmail()`
* `save()`

Avoid ambiguous or generic names whenever a more expressive alternative exists.

---

# Immutability

* Prefer immutable objects wherever practical.
* Mark fields as `final` whenever they do not change.
* Return defensive copies of mutable collections.
* Do not expose internal mutable state.

---

# Null Handling

* Validate constructor and method arguments using `Objects.requireNonNull()`.
* Fail fast when invalid input is detected.
* Use `Optional` for repository lookup operations where an entity may not exist.
* Do not return `null` from repository query methods.

---

# Exceptions

* Throw domain-specific exceptions for business rule violations.
* Exception messages should clearly describe the violated business rule.
* Do not silently ignore invalid operations.

Preferred:

* `"Only users pending verification can be activated."`
* `"User already has role ADMIN."`

Avoid:

* `"Invalid state"`
* `"Operation failed"`
* `"Error"`

---

# Testing

* Write unit tests for all business behavior.
* Use JUnit 5.
* Use AssertJ for assertions.
* Use Mockito for mocking dependencies.
* Use `@DisplayName` for every test.
* Test business behavior rather than implementation details.

---

# Package Organization

Organize packages by **business capability** or **architectural responsibility**, not by Java language constructs.

Prefer:

```text
domain
├── model
├── repository
├── valueobject
└── exception

application
├── command
├── response
├── service
└── usecase
```

Avoid:

```text
entity
dto
enum
util
helper
manager
```

---

# Documentation

* Add Javadoc to all public domain types.
* Document business rules rather than implementation details.
* Keep Architecture Decision Records (ADRs) up to date.
* Maintain project documentation alongside code changes.

---

# Formatting

* Use consistent indentation and formatting.
* Keep methods concise and focused.
* Prefer early returns to reduce nesting.
* Remove unused imports and dead code.
* Follow the project's formatting rules before committing.

---

# Summary

NovaBank emphasizes:

* Constructor injection only.
* No field injection.
* Rich Domain Models.
* Framework-independent Domain layer.
* Immutable Value Objects.
* Expressive business methods.
* Clear package organization.
* Comprehensive unit testing.
* Readable, maintainable, and production-quality code.
