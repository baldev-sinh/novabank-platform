# Identity Context

## Responsibilities

* User registration
* Authentication
* Authorization
* JWT token management
* Role management
* Password reset

## Aggregate Root

User

## Owned Data

* User
* Role
* Permission
* RefreshToken

## Published Events

* UserRegistered
* UserActivated
* UserLocked
* PasswordChanged

## Consumed Events

* None

---

# Customer Context

## Responsibilities

* Customer profile management
* Customer onboarding
* KYC verification
* Contact information management

## Aggregate Root

Customer

## Entities

* Customer
* CustomerAddress
* KYCRecord

## Published Events

* CustomerCreated
* CustomerUpdated
* CustomerVerified
* CustomerSuspended

## Consumed Events

* UserRegistered

---

# Account Context

## Responsibilities

* Account lifecycle management
* Account status management
* Balance projections
* Funds hold management

## Aggregate Root

Account

## Entities

* Account
* AccountHold
* AccountBalanceProjection

## Value Objects

* AccountNumber
* Money

## Published Events

* AccountOpened
* AccountClosed
* FundsHeld
* HoldReleased
* BalanceUpdated

## Consumed Events

* CustomerVerified

---

# Payment Context

## Responsibilities

* Money transfer orchestration
* Payment lifecycle management
* Payment status tracking
* Saga orchestration

---

# Ledger Context

## Responsibilities

* Double-entry accounting
* Ledger transaction posting
* Financial source of truth
* Reconciliation support

---

# Notification Context

## Responsibilities

* Email notifications
* SMS notifications
* Push notifications

---

# Audit Context

## Responsibilities

* Audit trail management
* Compliance logging
* Event archival
