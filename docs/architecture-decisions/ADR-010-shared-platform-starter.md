# ADR-010: Shared Platform Starter for Cross-Cutting Concerns

## Status

Accepted (2026-11-16)

## Context

The outbox, idempotent consumers, dead-letter handling, JWT resource-server setup, RFC 9457 error
handling and Idempotency-Key support were needed in every service. Copying them means fixing each
bug several times.

Options considered:

1. A shared library, `libs/novabank-platform-starter`, with technical code only.
2. A copy in every service.

## Decision

Option 1, as a Spring Boot auto-configuration library:

* Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
* Technology dependencies are optional; each piece switches on only when the service uses that
  technology, and backs off when the service defines its own bean (e.g. the auth service's own
  `JwtDecoder`).
* Library tables (outbox, processed events, idempotency keys) use `JdbcClient`, not JPA, so services
  need no entity scanning for them; each service owns their schema in its own Flyway migrations.
* **No domain code.** Services keep their own application ports and adapt them to the library.

## Consequences

### Benefits

* One place to fix and test cross-cutting behaviour.
* New services start with production-grade messaging, security and error handling.

### Trade-offs

* All services build against the same library version; a breaking change touches every service.
* Each service must still create the library's tables (documented in the library's Javadoc).
