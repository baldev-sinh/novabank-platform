# ADR-003: Idempotency Strategy

## Status

Accepted

## Context

Network failures, retries, duplicate submissions, and Kafka redelivery can result in duplicate payment processing.

Financial transactions must be executed exactly once from a business perspective.

## Decision

NovaBank will implement idempotency at both API and event-processing layers.

### API Layer

Clients must provide an Idempotency-Key header.

Payment Service will store the idempotency key and associate it with a single payment.

Duplicate requests with the same idempotency key will return the original payment response.

### Event Layer

All Kafka consumers must be idempotent.

Consumers will maintain a processed event registry and ignore previously processed events.

## Consequences

### Benefits

* Prevent duplicate transfers
* Safe retries
* Safe Kafka redelivery handling
* Improved reliability

### Trade-offs

* Additional storage requirements
* Additional lookup overhead

## Implementation Notes

Idempotency keys must be unique.

Database unique constraints provide the final protection against race conditions.

Event consumers must persist processed event identifiers before acknowledging successful processing.
