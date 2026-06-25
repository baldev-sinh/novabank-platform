# NovaBank System Context Diagram

# Actors

## Customer

Uses mobile/web banking channels to:

* Register
* Login
* View Accounts
* Transfer Funds
* View Transactions

---

## Operations Team

Uses operational tooling to:

* Investigate Payments
* View Audit Logs
* Monitor Reconciliation
* Resolve Exceptions

---

## Administrator

Uses administration tooling to:

* Manage Users
* Manage Roles
* Manage Permissions
* Configure System Settings

---

# External Systems

## Auth0 (Future)

Identity Provider

Responsibilities:

* Authentication
* Authorization
* MFA

---

## Email Provider

Responsibilities:

* Send Email Notifications

---

## SMS Provider

Responsibilities:

* Send SMS Notifications

---

# Core Services

## API Gateway

Single entry point into NovaBank.

---

## Identity Service

Authentication and authorization.

---

## Customer Service

Customer profile and KYC management.

---

## Account Service

Account lifecycle, account status, holds and balance projections.

---

## Payment Service

Payment orchestration and saga management.

---

## Ledger Service

Financial source of truth and double-entry accounting.

---

## Notification Service

Email, SMS and push notifications.

---

## Audit Service

Audit trail and compliance logging.

---

# Infrastructure Components

## Kafka

Event backbone for asynchronous communication.

---

## PostgreSQL

Database per service.

---

## Docker

Local development runtime.

---

# Data Stores

identity_db

customer_db

account_db

payment_db

ledger_db

notification_db

audit_db

---

# Communication Patterns

## Synchronous

Payment Service → Account Service

Payment Service → Customer Service

API Gateway → Services

---

## Asynchronous

Payment Service → Kafka

Ledger Service → Kafka

Notification Service → Kafka

Audit Service → Kafka
