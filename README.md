# NovaBank

> A production-grade digital banking platform built using Domain-Driven Design (DDD), Clean Architecture, and Microservices.

## Overview

NovaBank is a learning and portfolio project that demonstrates how to design and build an enterprise-grade banking platform using modern backend and frontend technologies.

The project follows industry best practices including:

- Domain-Driven Design (DDD)
- Clean Architecture
- Hexagonal Architecture (Ports & Adapters)
- Microservices
- Event-Driven Design
- RESTful APIs
- JWT-based Authentication
- Test-Driven Development (TDD)
- CI/CD-ready project structure

The goal is not only to build a banking application, but to demonstrate production-quality software engineering practices that are commonly used in enterprise environments.

---

## Architecture

The platform is organised into independent services with clear responsibilities.

```
                +-----------------------+
                |     Angular Web       |
                +-----------+-----------+
                            |
                            v
                +-----------------------+
                |  API Gateway (Future) |
                +-----------+-----------+
                            |
        +-------------------+-------------------+
        |                   |                   |
        v                   v                   v
+---------------+   +---------------+   +---------------+
| Identity/Auth |   | Customer      |   | Account       |
| Service       |   | Service       |   | Service       |
+---------------+   +---------------+   +---------------+
                            |
                            v
                    Additional Services
```

Each service owns its domain, database, and business rules.

---

## Repository Structure

```
novabank-platform/
│
├── docs/               # Architecture Decision Records, standards and documentation
├── diagrams/           # Architecture and design diagrams
├── infrastructure/     # Shared infrastructure resources
├── services/           # Backend microservices
│
├── novabank-web/       # Angular frontend
│
├── pom.xml             # Parent Maven project
└── README.md
```

---

## Technology Stack

### Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Maven
- JWT
- JUnit 5
- Mockito

### Frontend

- Angular 19
- TypeScript
- RxJS
- SCSS
- Angular Router
- Reactive Forms

### DevOps & Tooling

- GitHub
- Maven Wrapper
- Docker (planned)
- GitHub Actions (planned)

---

## Current Features

### Backend

- Authentication Service
- User Registration
- User Login
- BCrypt Password Encoding
- JWT Generation
- JWT Validation
- JWT Authentication Filter
- Global Exception Handling
- OpenAPI / Swagger Documentation

### Frontend

- Application Layout
- Login Page
- Reactive Forms
- Authentication Service
- Token Storage Service
- JWT HTTP Interceptor
- Authentication Guard
- Guest Guard
- Dashboard Shell
- Logout Functionality

---

## Getting Started

### Clone the repository

```bash
git clone https://github.com/<your-username>/novabank-platform.git

cd novabank-platform
```

---

### Backend

Run the authentication service:

```bash
./mvnw spring-boot:run
```

or on Windows:

```bash
mvnw.cmd spring-boot:run
```

---

### Frontend

```bash
cd novabank-web

npm install

ng serve
```

The application will be available at:

```
http://localhost:4200
```

---

## Documentation

Project documentation is maintained under the `docs` directory.

Documentation includes:

- Architecture Decision Records (ADRs)
- Coding Standards
- Service Contracts
- Development Guidelines
- Project Roadmap

---

## Development Principles

NovaBank follows several engineering principles:

- Single Responsibility Principle
- SOLID Principles
- Domain-Driven Design
- Clean Architecture
- Hexagonal Architecture
- Convention over Configuration
- Testability
- Separation of Concerns

---

## Branching Strategy

Feature development follows a feature branch workflow.

Example:

```
feature/NVB-027-login-page
feature/NVB-030-auth-guard
feature/NVB-032-dashboard-shell
```

Each feature is developed through small, reviewable pull requests.

---

## Roadmap

The project is being implemented incrementally.

Current areas of development include:

- Identity & Authentication
- Customer Management
- Account Management
- Payments
- Transaction History
- Notifications
- Observability
- Deployment & CI/CD

---

## Contributing

Please read the project's `CONTRIBUTING.md` before submitting changes.

---

## License

This project is licensed under the MIT License.
