# NovaBank Event Catalog

This document defines the domain events exchanged between NovaBank microservices.

Each event represents a significant business occurrence and enables asynchronous communication between services while maintaining loose coupling.

---

# Standard Event Envelope

Every event published by NovaBank must contain the following metadata:

| Field            | Description                                                |
| ---------------- | ---------------------------------------------------------- |
| `eventId`        | Unique identifier for the event.                           |
| `eventType`      | Name of the business event.                                |
| `eventTimestamp` | UTC timestamp when the event occurred.                     |
| `correlationId`  | Correlates events belonging to the same business workflow. |
| `payload`        | Event-specific business data.                              |

---

# Payment Events

## PaymentInitiated

**Description**

Raised when a customer initiates a payment request.

**Producer**

* Payment Service

**Consumers**

* Payment Service

---

## AccountsValidated

**Description**

Raised after validating that both the sender and beneficiary accounts exist and are eligible for the transaction.

**Producer**

* Payment Service

**Consumers**

* Payment Service

---

## FundsHeld

**Description**

Raised when the sender's funds have been successfully reserved for the payment.

**Producer**

* Account Service

**Consumers**

* Payment Service

---

## HoldReleased

**Description**

Raised when previously reserved funds are released due to payment cancellation or failure.

**Producer**

* Account Service

**Consumers**

* Payment Service

---

## FundsHoldFailed

**Description**

Raised when funds cannot be reserved because of insufficient balance or another business rule.

**Producer**

* Account Service

**Consumers**

* Payment Service

---

## LedgerTransactionPosted

**Description**

Raised after debit and credit ledger entries have been successfully recorded.

**Producer**

* Ledger Service

**Consumers**

* Payment Service
* Account Service
* Audit Service

---

## LedgerPostingFailed

**Description**

Raised when the Ledger Service is unable to record the financial transaction.

**Producer**

* Ledger Service

**Consumers**

* Payment Service

---

## BalancesUpdated

**Description**

Raised after account balance projections have been updated following successful ledger processing.

**Producer**

* Account Service

**Consumers**

* Payment Service

---

## PaymentCompleted

**Description**

Raised when the payment workflow completes successfully.

**Producer**

* Payment Service

**Consumers**

* Notification Service
* Audit Service

---

## PaymentFailed

**Description**

Raised when the payment workflow terminates unsuccessfully.

**Producer**

* Payment Service

**Consumers**

* Notification Service
* Audit Service

---

# Event Naming Conventions

All domain events should:

* Represent completed business actions.
* Be named using the past tense.
* Be immutable after publication.
* Avoid exposing implementation details.
* Contain only business-relevant data.

Examples:

* `PaymentInitiated`
* `FundsHeld`
* `PaymentCompleted`
* `UserRegistered`
* `PasswordChanged`

Avoid names such as:

* `ProcessPayment`
* `UpdateBalance`
* `HandleEvent`

---

# Versioning

* Event schemas should be backward compatible whenever possible.
* Breaking changes require a new event version.
* Consumers should ignore unknown fields to support forward compatibility.

---

# Reliability

* Events should be published only after the business transaction succeeds.
* Event publication should support at-least-once delivery.
* Consumers must be idempotent to safely process duplicate events.
* Correlation IDs must be propagated throughout the payment workflow for traceability and observability.

---

# Future Event Catalog

As NovaBank evolves, additional event categories will be introduced, including:

* Authentication Events
* Customer Events
* Account Events
* Card Events
* Beneficiary Events
* Loan Events
* Notification Events
* Audit Events
* Fraud Detection Events
