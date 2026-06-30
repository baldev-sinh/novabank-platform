# NovaBank Service Contracts

This document defines the responsibilities, ownership boundaries, and integration contracts for each microservice within the NovaBank platform.

Each service owns its data and business rules. Communication between services occurs through well-defined APIs and asynchronous domain events.

---

# Auth Service

## Purpose

Provides authentication, authorization, and identity management for the NovaBank platform.

## Responsibilities

* User Registration
* Authentication
* Authorization
* JWT Token Management
* Refresh Token Management
* Role Management

## Owns

* User (Aggregate)
* Role
* RefreshToken

## Database

`auth_db`

## Published Events

* UserRegistered
* UserActivated
* UserLocked
* PasswordChanged

## Consumed Events

None

---

# Customer Service

## Purpose

Manages customer identity, onboarding, and regulatory information.

## Responsibilities

* Customer Registration
* Customer Profile Management
* Customer Lifecycle
* KYC Management
* Contact Information

## Owns

* Customer (Aggregate)
* CustomerAddress
* KYCRecord

## Database

`customer_db`

## Published Events

* CustomerCreated
* CustomerVerified
* CustomerSuspended

## Consumed Events

* UserRegistered

---

# Account Service

## Purpose

Manages customer bank accounts and available balances.

## Responsibilities

* Account Lifecycle
* Account Status Management
* Funds Hold Management
* Balance Projection
* Account Balance Inquiry

## Owns

* Account (Aggregate)
* AccountHold
* AccountBalanceProjection

## Database

`account_db`

## Published Events

* AccountOpened
* FundsHeld
* HoldReleased
* BalancesUpdated

## Consumed Events

* CustomerVerified
* LedgerTransactionPosted

---

# Payment Service

## Purpose

Coordinates end-to-end payment execution using the Saga pattern.

## Responsibilities

* Payment Orchestration
* Payment Lifecycle Management
* Saga Coordination
* Idempotency Management
* Payment Status Tracking

## Owns

* Payment (Aggregate)

## Database

`payment_db`

## Published Events

* PaymentInitiated
* PaymentCompleted
* PaymentFailed
* PaymentReversed

## Consumed Events

* FundsHeld
* FundsHoldFailed
* LedgerTransactionPosted
* LedgerPostingFailed
* BalancesUpdated

---

# Ledger Service

## Purpose

Maintains the immutable financial record of the platform.

## Responsibilities

* Double-Entry Accounting
* Ledger Posting
* Journal Management
* Financial Source of Truth
* Balance Reconciliation

## Owns

* LedgerTransaction (Aggregate)
* LedgerEntry

## Database

`ledger_db`

## Published Events

* LedgerTransactionPosted
* LedgerPostingFailed

## Consumed Events

* PaymentInitiated

---

# Notification Service

## Purpose

Delivers customer notifications across supported communication channels.

## Responsibilities

* Email Notifications
* SMS Notifications
* Push Notifications
* Notification Delivery Tracking

## Owns

* Notification

## Database

`notification_db`

## Published Events

* NotificationSent

## Consumed Events

* PaymentCompleted
* PaymentFailed
* UserRegistered
* PasswordChanged

---

# Audit Service

## Purpose

Maintains an immutable audit trail for regulatory compliance and operational traceability.

## Responsibilities

* Audit Trail
* Compliance Logging
* Event Archival
* Operational Audit

## Owns

* AuditRecord

## Database

`audit_db`

## Published Events

None

## Consumed Events

* UserRegistered
* CustomerVerified
* PaymentCompleted
* PaymentFailed
* LedgerTransactionPosted

---

# Architectural Principles

* Each service owns its data and business rules.
* Each service has its own dedicated database.
* No service may access another service's database directly.
* Services communicate through APIs and domain events only.
* Business capabilities are isolated within service boundaries.
* Events represent completed business actions and are immutable.
* Cross-service workflows are coordinated using event-driven choreography where appropriate.
* Each service can be developed, deployed, and scaled independently.
