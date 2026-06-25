# NovaBank Vision Document

## Project Overview

NovaBank is a cloud-native digital banking platform built using Domain-Driven Design (DDD), Microservices Architecture, and Event-Driven Architecture.

The platform enables customers to securely manage accounts, transfer funds, view transaction history, and receive real-time notifications.

The system is designed to demonstrate production-grade banking principles including:

* Double-entry ledger accounting
* Event-driven workflows
* Distributed transactions
* Secure authentication and authorization
* Auditability
* Observability
* Scalability
* Fault tolerance

---

## Business Goals

The primary goals of NovaBank are:

1. Provide secure customer onboarding and authentication.
2. Enable account creation and management.
3. Support real-time money transfers between accounts.
4. Maintain accurate and immutable financial records.
5. Ensure complete auditability of all financial operations.
6. Provide reliable customer notifications.
7. Demonstrate production-grade microservice architecture.

---

## Functional Requirements

### Customer Management

* Register customer
* Update customer profile
* View customer profile

### Authentication

* User login
* JWT token generation
* Role-based access control

### Account Management

* Open account
* View account details
* View available balance

### Payments

* Transfer funds
* View payment history
* Track payment status

### Ledger

* Record debit entries
* Record credit entries
* Maintain immutable financial records

### Notifications

* Email notifications
* Transaction alerts

### Audit

* Record all critical system actions
* Maintain complete audit trails

---

## Non-Functional Requirements

### Security

* OAuth2 Authentication
* JWT Authorization
* Encrypted communication via HTTPS

### Reliability

* No duplicate financial transactions
* Event delivery guarantees

### Scalability

* Independently deployable services
* Horizontal scaling support

### Availability

* Target uptime: 99.9%

### Observability

* Centralized logging
* Distributed tracing
* Metrics collection

### Auditability

* Immutable ledger records
* Full transaction history

---

## Architectural Principles

### Domain-Driven Design

Services are aligned to business domains and bounded contexts.

### Database Per Service

Each microservice owns its database.

### Event-Driven Communication

Services communicate asynchronously using Kafka.

### Eventual Consistency

Cross-service workflows use Saga patterns.

### Ledger as Source of Truth

Financial balances are derived from ledger entries.

The ledger is the authoritative source of financial data.

---

## Technology Stack

* Java 21
* Spring Boot 3.5.x
* Spring Security
* Spring Data JPA
* PostgreSQL
* Apache Kafka
* Docker
* OpenTelemetry
* Prometheus
* Grafana

---

## Success Criteria

A customer can:

1. Register and authenticate.
2. Open an account.
3. Transfer funds.
4. View transaction history.
5. Receive notifications.

All financial operations must be auditable, reliable, and traceable across services.
