# ADR-002: Payment Workflow Strategy

## Status

Accepted

## Context

Money transfer operations span multiple services and databases.

Distributed database transactions are not feasible in a microservices architecture.

## Decision

NovaBank will use Saga Orchestration for payment workflows.

Payment Service will act as the workflow owner and orchestrator.

Financial side effects will be executed by downstream services through events and commands.

## Consequences

### Benefits

* Clear ownership of payment workflows
* Easier monitoring and troubleshooting
* Explicit compensation handling
* Improved auditability

### Trade-offs

* More orchestration logic in Payment Service
* Additional workflow state management

## Implementation Notes

Payment Service owns payment status.

Ledger Service owns financial postings.

Account Service owns balance projections and funds holds.
