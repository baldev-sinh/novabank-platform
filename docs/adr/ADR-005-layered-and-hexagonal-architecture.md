# ADR-005: Layered and Hexagonal Architecture

* **Status:** Accepted
* **Date:** 2026-06-29
* **Decision Makers:** NovaBank Engineering Team

---

# Context

NovaBank is designed as a long-lived banking platform that must evolve independently of frameworks, databases, and external technologies.

The system will expose multiple delivery mechanisms (REST APIs, messaging, scheduled jobs) while supporting different infrastructure implementations (PostgreSQL, messaging brokers, cloud services, etc.).

To ensure maintainability and flexibility, NovaBank adopts a combination of **Layered Architecture**, **Hexagonal Architecture (Ports and Adapters)**, **Clean Architecture**, and **Domain-Driven Design (DDD)**.

---

# Decision

NovaBank organizes its codebase into distinct architectural layers with clear responsibilities and dependency boundaries.

The architecture consists of:

* Domain Layer
* Application Layer
* Infrastructure Layer

Dependencies always point inward toward the Domain layer.

Business rules remain independent of frameworks and external systems.

---

# Architectural Layers

## Domain Layer

The Domain layer contains the core business model.

It includes:

* Aggregates
* Entities
* Value Objects
* Domain Services
* Repository interfaces (Ports)
* Domain Exceptions

The Domain layer contains all business rules and must not depend on any framework or infrastructure technology.

---

## Application Layer

The Application layer orchestrates business use cases.

Responsibilities include:

* Executing use cases.
* Coordinating domain objects.
* Calling repository interfaces.
* Managing application workflows.
* Returning application responses.

The Application layer does **not** contain business rules.

Business decisions always remain inside the Domain layer.

---

## Infrastructure Layer

The Infrastructure layer contains technical implementations.

Examples include:

* JPA repositories.
* Spring configuration.
* REST controllers.
* Database adapters.
* Messaging adapters.
* External service integrations.

Infrastructure depends on the Domain and Application layers but never the other way around.

---

# Why the Domain Is Framework-Independent

The Domain layer represents business knowledge rather than implementation details.

Business rules should continue to work regardless of whether the application uses:

* Spring Boot
* Hibernate
* PostgreSQL
* MongoDB
* Kafka
* REST APIs

Keeping the Domain independent provides several benefits:

* Business logic is portable.
* Unit tests require no framework.
* Infrastructure can change without affecting business rules.
* The model remains focused on the problem domain.

---

# Why the Application Layer Orchestrates Use Cases

The Application layer coordinates interactions between the Domain and Infrastructure layers.

Typical responsibilities include:

* Receiving a command.
* Loading aggregates.
* Invoking business methods.
* Persisting changes.
* Returning a response.

For example, registering a user consists of:

1. Validate email uniqueness.
2. Create a `User` aggregate.
3. Persist the aggregate.
4. Return the registration response.

The Application layer coordinates these steps but delegates business decisions to the Domain.

---

# Why Repositories Are Interfaces

Repositories define persistence contracts within the Domain layer.

They describe **what** operations are required rather than **how** those operations are implemented.

Example operations include:

* Save a user.
* Find a user by ID.
* Find a user by email.
* Check whether an email already exists.

Concrete implementations belong to the Infrastructure layer.

This separation allows the persistence technology to change without impacting business logic.

---

# Why Infrastructure Contains Adapters

The Infrastructure layer adapts external technologies to the needs of the application.

Examples include:

* Spring Data JPA repositories implementing domain repository interfaces.
* REST controllers translating HTTP requests into application commands.
* Database mappings.
* Messaging consumers and producers.
* External service clients.

Infrastructure components translate between the outside world and the application's internal model.

---

# Dependency Direction

Dependencies always point inward.

```text
Presentation / API
        │
        ▼
Application
        │
        ▼
Domain
        ▲
        │
Infrastructure (implements ports)
```

The Domain layer depends on nothing.

The Application layer depends only on the Domain.

The Infrastructure layer depends on both the Domain and the Application.

This dependency direction ensures that business logic remains isolated from technical concerns.

---

# Benefits for Testing

This architecture enables fast and reliable testing.

### Domain Tests

* No Spring context.
* No database.
* Pure unit tests.
* Fast execution.

### Application Tests

* Mock repository interfaces.
* Verify orchestration logic.
* No infrastructure required.

### Infrastructure Tests

* Validate adapters.
* Verify persistence mappings.
* Test external integrations independently.

Each layer can be tested in isolation.

---

# Benefits for Maintainability

Adopting Layered and Hexagonal Architecture provides several long-term benefits:

* Clear separation of responsibilities.
* Framework-independent business logic.
* Easier unit testing.
* Replaceable infrastructure components.
* Reduced coupling between layers.
* Improved readability and maintainability.
* Better support for future architectural evolution.
* Alignment with Clean Architecture and Domain-Driven Design.

---

# Consequences

### Positive

* Stable and framework-independent Domain layer.
* Clear architectural boundaries.
* High testability.
* Flexible infrastructure choices.
* Easier onboarding for new developers.
* Better long-term maintainability.

### Trade-offs

* More initial design effort.
* Additional abstraction through ports and adapters.
* More project structure than traditional layered applications.

---

# Decision Summary

NovaBank adopts Layered and Hexagonal Architecture to ensure that business logic remains independent of frameworks and infrastructure.

The Domain layer owns business rules, the Application layer orchestrates use cases, repository interfaces define persistence contracts, and the Infrastructure layer provides concrete adapters.

This architecture promotes maintainability, testability, flexibility, and long-term evolution while remaining aligned with Domain-Driven Design and Clean Architecture principles.
