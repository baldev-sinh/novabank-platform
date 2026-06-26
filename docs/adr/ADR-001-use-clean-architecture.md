# ADR-001: Adopt Clean Architecture

## Status

Accepted

## Context

NovaBank is a long-lived banking platform composed of multiple microservices.
The project requires high maintainability, testability, and clear separation of concerns.

Traditional layered architectures tend to couple business logic with frameworks and persistence.

## Decision

Every microservice shall follow Clean Architecture combined with Domain-Driven Design principles.

The primary layers are:

- Domain
- Application
- Infrastructure
- Interfaces

The domain layer must remain independent of Spring Framework and persistence technologies.

## Consequences

### Benefits

- Framework-independent domain model
- Easier unit testing
- Better separation of concerns
- Easier technology replacement
- Improved maintainability

### Trade-offs

- More packages
- Higher initial complexity
- Requires architectural discipline
