# ADR-009: Money Representation

## Status

Accepted (2026-11-16)

## Context

Amounts appear in the domain, in PostgreSQL, in REST APIs and in Kafka events. Once events are
published, their format is hard to change.

Options considered:

1. **Decimal plus currency:** `BigDecimal` in Java, `NUMERIC(19,4)` in PostgreSQL, a decimal string
   plus an ISO 4217 code in JSON, e.g. `{"amount":"125.50","currency":"INR"}`.
2. **Integer minor units:** `long`, `BIGINT`, e.g. `{"amountMinor":12550,"currency":"INR"}`.

## Decision

Option 1, as the shared `Money` value object (`libs/novabank-platform-starter`):

* Exactly the currency's ISO 4217 number of decimals (INR 2, JPY 0, KWD 3). More decimals are
  **rejected, never rounded**.
* Arithmetic only between equal currencies.
* JSON amounts are strings, so no client ever parses money as a floating-point number.

## Consequences

### Benefits

* The ISO 20022 style used by banks; consumers need no per-currency scale table.
* Exact arithmetic; no silent rounding.

### Trade-offs

* Every boundary must validate the amount against the currency (done by `Money.of`).
* `NUMERIC(19,4)` covers every ISO 4217 currency (at most 4 decimals) but not crypto-style
  precision.
