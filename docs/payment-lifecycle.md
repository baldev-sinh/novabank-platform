# NovaBank Payment Lifecycle

This document describes the end-to-end lifecycle of a payment within the NovaBank platform.

The payment workflow follows an event-driven, saga-based architecture to ensure consistency across distributed microservices while avoiding distributed transactions.

---

# Overview

A payment progresses through several business states before reaching a terminal outcome.

The lifecycle ensures that:

* Customer balances remain consistent.
* Funds are never spent twice.
* Ledger entries are immutable.
* Every step is auditable.
* Failures are recoverable.

---

# Payment Lifecycle

```text
INITIATED
      │
      ▼
VALIDATED
      │
      ▼
FUNDS_HELD
      │
      ▼
LEDGER_POSTED
      │
      ▼
BALANCES_UPDATED
      │
      ▼
COMPLETED
```

Failure at any stage results in compensation and transitions to `FAILED`.

---

# Lifecycle States

## INITIATED

The customer submits a payment request.

### Business Rules

* Payment request received.
* Request identifier generated.
* Initial validation begins.

### Event

* `PaymentInitiated`

---

## VALIDATED

Business validation succeeds.

Validation includes:

* Sender account exists.
* Beneficiary account exists.
* Payment amount is valid.
* Currency is supported.
* Customer is authorized.
* Payment limits are respected.

### Event

* `AccountsValidated`

---

## FUNDS_HELD

Funds are reserved on the sender account.

No money has been transferred yet.

Reserved funds cannot be used for other transactions.

### Event

* `FundsHeld`

Failure:

* `FundsHoldFailed`

---

## LEDGER_POSTED

The Ledger Service records immutable financial entries.

Operations include:

* Debit sender account.
* Credit beneficiary account.

Ledger entries become the system of record.

### Event

* `LedgerTransactionPosted`

Failure:

* `LedgerPostingFailed`

---

## BALANCES_UPDATED

The Account Service updates balance projections based on the ledger entries.

Available and reserved balances are recalculated.

### Event

* `BalancesUpdated`

---

## COMPLETED

The payment workflow has completed successfully.

Notifications and audit records may now be generated.

### Event

* `PaymentCompleted`

---

## FAILED

The payment cannot be completed.

Possible reasons include:

* Validation failure.
* Insufficient funds.
* Ledger failure.
* Internal processing error.

If funds were previously reserved, they are released before the payment is marked as failed.

### Events

* `HoldReleased`
* `PaymentFailed`

---

# Compensation Workflow

NovaBank uses the Saga pattern for distributed consistency.

If a failure occurs after funds have been reserved:

```text
Funds Held
      │
      ▼
Ledger Failure
      │
      ▼
Release Funds
      │
      ▼
Payment Failed
```

Compensation restores the system to a consistent business state without requiring distributed transactions.

---

# Event Flow

```text
Customer

    │

    ▼

PaymentInitiated

    │

    ▼

AccountsValidated

    │

    ▼

FundsHeld

    │

    ▼

LedgerTransactionPosted

    │

    ▼

BalancesUpdated

    │

    ▼

PaymentCompleted
```

Failure path:

```text
FundsHeld

    │

    ▼

LedgerPostingFailed

    │

    ▼

HoldReleased

    │

    ▼

PaymentFailed
```

---

# Service Responsibilities

## Payment Service

Responsible for:

* Payment orchestration.
* Business workflow.
* Saga coordination.
* Publishing payment events.

---

## Account Service

Responsible for:

* Account validation.
* Holding funds.
* Releasing funds.
* Updating projected balances.

---

## Ledger Service

Responsible for:

* Recording immutable financial transactions.
* Maintaining the financial source of truth.

---

## Notification Service

Responsible for:

* Customer notifications.
* Payment confirmations.
* Failure notifications.

---

## Audit Service

Responsible for:

* Recording business events.
* Supporting compliance and regulatory reporting.

---

# Business Invariants

NovaBank guarantees the following:

* A payment cannot complete without successful ledger posting.
* Funds must be successfully reserved before ledger posting.
* Ledger entries are immutable.
* Every payment has a unique identifier.
* Every payment is traceable using a correlation ID.
* Every state transition is auditable.
* Reserved funds are released if payment processing fails after a successful hold.

---

# Future Enhancements

Future versions of the payment lifecycle may include:

* Scheduled payments.
* Recurring payments.
* Multi-currency payments.
* FX conversion.
* Fraud detection.
* AML screening.
* Payment cancellation.
* Payment reversal.
* Chargeback processing.
* Real-time payment settlement.
