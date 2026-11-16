# ADR-011: Funding Accounts via Operations Deposits

## Status

Accepted (2026-11-16)

## Context

New accounts start at zero. Money must enter the bank somehow, in a way that keeps the ledger
balanced and auditable.

Options considered:

1. An operations-only deposit endpoint that posts double-entry from a bank `SETTLEMENT` account.
2. Seed data in the local profile only.

## Decision

Option 1. `POST /api/v1/admin/deposits` (roles OPERATIONS or ADMIN, Idempotency-Key required)
starts a DEPOSIT payment. The ledger debits the `SETTLEMENT` account (an asset, one per currency)
and credits the customer's account (a liability). No hold is needed: no customer money is spent.

## Consequences

### Benefits

* Money entering the bank is a payment like any other: idempotent, saga-tracked, double-entry,
  reconcilable.
* Works in every environment, not just locally.

### Trade-offs

* Staff users can only get the OPERATIONS role by a direct database change until staff
  provisioning exists.
* A deposit doesn't check the target account's status (the ledger doesn't know it); a later
  version should validate it in the account service first.
