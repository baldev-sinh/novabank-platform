# NovaBank Engineering Standards

## Java Version

Java 21

## Spring Boot Version

Spring Boot 3.5.x

## Database

PostgreSQL

## Build Tool

Maven

## Source Control

Git

## Branching Strategy

Main Branch:

* main

Feature Branches:

* feature/<feature-name>

Bug Fix Branches:

* bugfix/<issue-name>

## Commit Convention

feat(scope): description

fix(scope): description

docs(scope): description

refactor(scope): description

test(scope): description

## API Standards

All APIs must be versioned.

Example:

/api/v1/accounts

/api/v1/payments

## Logging Standards

Use structured JSON logging.

Correlation ID must be present in all requests.

## Security Standards

JWT Authentication

OAuth2 Authorization

HTTPS Only

## Database Standards

Database Per Service

No Cross-Service Database Access

## Event Standards

All events must contain:

eventId

eventType

eventTimestamp

correlationId

payload

## Testing Standards

Unit Tests

Integration Tests

Testcontainers

Contract Tests

## Documentation Standards

Every service must contain:

README.md

API documentation

Event documentation
