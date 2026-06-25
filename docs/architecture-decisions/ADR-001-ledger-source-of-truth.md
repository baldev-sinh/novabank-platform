# ADR-001: Ledger as Source of Truth

## Status

Accepted

## Context

NovaBank requires accurate and auditable financial records.

Account balances must remain consistent across distributed services and support reconstruction of financial history.

## Decision

Ledger Service will be the authoritative source of financial truth.

All money movements will be recorded as immutable ledger entries.

Account Service will maintain balance projections for fast reads.

Balances stored in Account Service are derived data and can be rebuilt from ledger records.

## Consequences

### Benefits

* Complete auditability
* Immutable transaction history
* Financial correctness
* Easier reconciliation

### Trade-offs

* Eventual consistency between Ledger and Account services
* Additional projection update logic required

## Implementation Notes

Ledger entries are never updated or deleted.

Account balances are updated through ledger events.

Balance queries should use Account Service projections rather than recalculating from ledger records.
