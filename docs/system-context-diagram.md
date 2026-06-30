# NovaBank System Context Diagram

This document provides a high-level view of the NovaBank platform, its primary actors, external systems, internal services, infrastructure components, and communication patterns.

---

# Primary Actors

## Customer

Uses the mobile and web banking applications to:

* Register
* Authenticate
* Manage Profile
* View Accounts
* View Transactions
* Transfer Funds

---

## Operations Team

Uses operational tooling to:

* Investigate Payments
* Investigate Failed Transactions
* Monitor Reconciliation
* Resolve Operational Exceptions
* View Audit Logs

---

## Administrator

Uses administration tooling to:

* Manage Users
* Manage Roles
* Configure System Settings

---

# External Systems

## External Identity Provider (Future)

Provides enterprise identity capabilities when required.

### Responsibilities

* Single Sign-On (SSO)
* Multi-Factor Authentication (MFA)
* Identity Federation

---

## Email Provider

### Responsibilities

* Send Email Notifications

---

## SMS Provider

### Responsibilities

* Send SMS Notifications

---

# Internal Services

## API Gateway

Single entry point for all client requests.

### Responsibilities

* Request Routing
* Authentication
* Authorization
* Rate Limiting
* Request Aggregation

---

## Auth Service

Provides authentication and identity management.

### Responsibilities

* User Registration
* Authentication
* Authorization
* Role Management
* JWT Management

---

## Customer Service

Manages customer information and regulatory data.

### Responsibilities

* Customer Profile
* Customer Onboarding
* KYC Management

---

## Account Service

Manages bank accounts and balances.

### Responsibilities

* Account Lifecycle
* Account Status
* Funds Hold Management
* Balance Projection

---

## Payment Service

Coordinates distributed payment execution.

### Responsibilities

* Payment Orchestration
* Payment Lifecycle
* Saga Coordination
* Idempotency

---

## Ledger Service

Maintains the financial source of truth.

### Responsibilities

* Double-Entry Accounting
* Ledger Posting
* Financial Transactions
* Reconciliation

---

## Notification Service

Delivers customer notifications.

### Responsibilities

* Email Notifications
* SMS Notifications
* Push Notifications

---

## Audit Service

Maintains immutable audit records.

### Responsibilities

* Audit Trail
* Compliance Logging
* Event Archival

---

# Infrastructure Components

## Apache Kafka

Event backbone for asynchronous communication between services.

---

## PostgreSQL

Dedicated database per microservice.

---

## Docker

Local development and containerized runtime.

---

## Prometheus (Future)

Metrics collection and monitoring.

---

## Grafana (Future)

Operational dashboards and visualization.

---

## OpenTelemetry (Future)

Distributed tracing and observability.

---

# Data Stores

* `auth_db`
* `customer_db`
* `account_db`
* `payment_db`
* `ledger_db`
* `notification_db`
* `audit_db`

Each microservice owns its database exclusively.

---

# Communication Patterns

## Synchronous

* Client → API Gateway
* API Gateway → Auth Service
* API Gateway → Customer Service
* API Gateway → Account Service
* API Gateway → Payment Service

---

## Asynchronous

* Payment Service → Apache Kafka
* Account Service → Apache Kafka
* Ledger Service → Apache Kafka
* Notification Service → Apache Kafka
* Audit Service → Apache Kafka

---

# Typical Payment Flow

```text
Customer
    │
    ▼
API Gateway
    │
    ▼
Payment Service
    │
    ▼
Account Service (Reserve Funds)
    │
    ▼
Ledger Service (Post Ledger Entries)
    │
    ▼
Account Service (Update Balance Projection)
    │
    ▼
Payment Service (Complete Payment)
    ├────────► Notification Service
    └────────► Audit Service
```

---

# Architectural Principles

* Domain-Driven Design (DDD).
* Clean Architecture.
* Hexagonal Architecture (Ports & Adapters).
* Database per service.
* No cross-service database access.
* Event-driven communication using Apache Kafka.
* Distributed transactions coordinated using the Saga Pattern.
* Business rules remain framework-independent within the Domain layer.
* Services are independently deployable and independently scalable.
