# NovaBank Service Contracts

# Identity Service

## Responsibilities

* Authentication
* Authorization
* JWT Token Management
* User Management
* Role Management

## Owns

* User
* Role
* Permission
* RefreshToken

## Database

identity_db

## Published Events

* UserRegistered
* UserActivated
* UserLocked
* PasswordChanged

## Consumed Events

None

---

# Customer Service

## Responsibilities

* Customer Profile
* Customer Onboarding
* KYC Management
* Contact Information

## Owns

* Customer
* CustomerAddress
* KYCRecord

## Database

customer_db

## Published Events

* CustomerCreated
* CustomerVerified
* CustomerSuspended

## Consumed Events

* UserRegistered

---

# Account Service

## Responsibilities

* Account Lifecycle
* Account Status
* Funds Hold Management
* Balance Projections

## Owns

* Account
* AccountHold
* AccountBalanceProjection

## Database

account_db

## Published Events

* AccountOpened
* FundsHeld
* HoldReleased
* BalanceUpdated

## Consumed Events

* CustomerVerified
* LedgerTransactionPosted

---

# Payment Service

## Responsibilities

* Payment Orchestration
* Payment Lifecycle
* Idempotency Management
* Saga Coordination

## Owns

* Payment

## Database

payment_db

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

## Responsibilities

* Double Entry Accounting
* Ledger Posting
* Financial Source of Truth
* Reconciliation Support

## Owns

* LedgerTransaction
* LedgerEntry

## Database

ledger_db

## Published Events

* LedgerTransactionPosted
* LedgerPostingFailed

## Consumed Events

* PaymentInitiated

---

# Notification Service

## Responsibilities

* Email Notifications
* SMS Notifications
* Push Notifications

## Database

notification_db

## Published Events

* NotificationSent

## Consumed Events

* PaymentCompleted
* PaymentFailed

---

# Audit Service

## Responsibilities

* Audit Trail
* Compliance Logging
* Event Archival

## Database

audit_db

## Published Events

None

## Consumed Events

* PaymentCompleted
* PaymentFailed
* LedgerTransactionPosted
* CustomerVerified
