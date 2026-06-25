# NovaBank Naming Conventions

# Java Packages

Use lowercase.

Example:

com.novabank.identity

com.novabank.payment

com.novabank.account

---

# Class Naming

Use PascalCase.

Examples:

User

Payment

LedgerTransaction

AccountHold

---

# Interfaces

Do not prefix with I.

Good:

PaymentRepository

Bad:

IPaymentRepository

---

# REST Controllers

Suffix:

Controller

Example:

PaymentController

AccountController

---

# Services

Prefer business-oriented names.

Good:

RegisterUserUseCase

AuthenticateUserUseCase

PlaceFundsHoldUseCase

Avoid generic names like:

UserService

PaymentService

unless they truly represent an application service.

---

# Request DTO

Suffix:

Request

Examples:

RegisterUserRequest

CreateAccountRequest

TransferFundsRequest

---

# Response DTO

Suffix:

Response

Examples:

UserResponse

PaymentResponse

AccountResponse

---

# Commands

Suffix:

Command

Examples:

RegisterUserCommand

TransferFundsCommand

---

# Queries

Suffix:

Query

Examples:

FindUserByIdQuery

FindAccountByNumberQuery

---

# Domain Events

Past tense.

Examples:

PaymentInitiated

FundsHeld

PaymentCompleted

LedgerTransactionPosted

Never:

CreatePayment

HoldFunds

---

# Kafka Topics

Lowercase with dots.

Examples:

identity.user.registered

payment.completed

ledger.transaction.posted

account.balance.updated

---

# Database Tables

Snake case.

Examples:

users

roles

permissions

payments

ledger_transactions

ledger_entries

account_holds

---

# Database Columns

Snake case.

Examples:

created_at

updated_at

account_number

customer_id

payment_id

---

# REST APIs

Plural resources.

Examples:

/api/v1/users

/api/v1/accounts

/api/v1/payments

Avoid verbs in URLs.

---

# Exceptions

Suffix:

Exception

Examples:

UserAlreadyExistsException

InsufficientFundsException

PaymentFailedException

---

# Enums

Upper snake case.

Examples:

ACTIVE

BLOCKED

PAYMENT_PENDING

PAYMENT_COMPLETED

---

# Constants

Upper snake case.

Examples:

MAX_LOGIN_ATTEMPTS

DEFAULT_PAGE_SIZE

JWT_EXPIRATION_MINUTES
