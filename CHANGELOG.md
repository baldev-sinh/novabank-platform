# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

---

## [Unreleased]

### Added

#### Project Foundation
- Bootstrapped NovaBank multi-module Maven project.
- Configured parent POM with centralized dependency and plugin management.
- Added Maven Enforcer Plugin requiring Java 21.
- Established project coding conventions and package structure.

#### Auth Service
- Created Spring Boot Auth Service.
- Configured PostgreSQL datasource.
- Added Flyway database migrations.
- Configured JPA with Hibernate validation.
- Added Spring Boot Actuator.

#### Domain Model
- Implemented User aggregate.
- Implemented UserStatus.
- Implemented RoleName.
- Implemented UserId value object.
- Implemented EmailAddress value object.
- Implemented PasswordHash value object.

#### Persistence
- Added JPA UserEntity.
- Added Spring Data JPA repository.
- Implemented JpaUserRepositoryAdapter.
- Added user_roles element collection.
- Added Flyway migrations for users and auth_user_roles tables.

#### User Registration
- Added Register User use case.
- Added RegisterUser REST endpoint.
- Added duplicate email validation.
- Added request validation.
- Added global exception handling.
- Added OpenAPI documentation.

#### Authentication
- Added BCrypt password hashing.
- Added Login use case.
- Added Login REST endpoint.
- Added invalid credential handling.
- Added JWT access token generation.
- Added JWT validation.
- Added JWT parsing.
- Added JwtUser security model.

#### Security
- Added JWT authentication filter.
- Integrated JWT authentication into Spring Security filter chain.
- Configured stateless session management.
- Added CORS configuration for Angular frontend.
- Added JWT configuration properties.

#### Testing
- Added domain model unit tests.
- Added RegisterUserService unit tests.
- Added JpaUserRepositoryAdapter unit tests.
- Added LoginUserService unit tests.
- Added JwtTokenService unit tests.

#### API Documentation
- Added Swagger/OpenAPI support.
- Documented Register API.
- Documented Login API.

### Changed

- Replaced plaintext password storage with BCrypt password hashing.
- Replaced login response payload with JWT access token response.
- Standardized API error responses.
- Improved validation error handling.
- Refactored repository mapping between domain and persistence models.
- Updated security configuration for stateless JWT authentication.

### Fixed

- Resolved Flyway compatibility issues with PostgreSQL 18.
- Fixed Flyway baseline migration issues.
- Corrected JPA entity mappings.
- Improved null validation across services and adapters.

### Added
- Added JWT authentication filter.
- Integrated JWT authentication into Spring Security filter chain.
- Added stateless session management.
