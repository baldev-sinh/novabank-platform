# NovaBank Service Contracts

What each service owns, exposes and exchanges. Each service has its own PostgreSQL database and
never reads another service's database. Services talk through REST (clients → services) and Kafka
(service → service). Message formats are in [event-catalog.md](event-catalog.md); the payment flow
is in [payment-lifecycle.md](payment-lifecycle.md).

Shared rules for every service:

- **Security:** OAuth2 resource server. Access tokens are RS256 JWTs issued by the auth service and
  validated against its JWKS (`iss` and `aud` checked). Roles come from the `roles` claim.
- **Errors:** RFC 9457 Problem Details (`application/problem+json`); validation errors carry an
  `errors` list. Someone else's resource is a 404, the same as a missing one.
- **Create operations that customers may legitimately repeat** require an `Idempotency-Key`
  header (ADR-003): a retry returns the stored result, the same key with a different body is 422.
- **Messaging:** transactional outbox (written in the business transaction), idempotent consumers
  (processed-events registry), retries with backoff, then a `<topic>-dlt` dead-letter topic.
  Business refusals are reply events, never dead letters.
- Cross-cutting code lives in `libs/novabank-platform-starter` (ADR-010).

---

## Auth Service (8081, `auth-db`)

**Owns:** users, roles, email verification tokens (hashes only).

| Endpoint | Who | Purpose |
|---|---|---|
| `POST /api/v1/users/register` | public | Create an account (status PENDING_VERIFICATION); sends a verification email |
| `POST /api/v1/users/verify-email` | public | Verify with the emailed token → ACTIVE |
| `POST /api/v1/users/verify-email/resend` | public | Always 202 (no account enumeration) |
| `POST /api/v1/auth/login` | public | Access token; ACTIVE users only (unverified: 403 `urn:novabank:problem:email-not-verified`) |
| `GET /api/v1/auth/me` | signed in | Current user |
| `GET /.well-known/jwks.json` | public | Public signing keys |
| `POST /api/v1/admin/users/lookup` | ADMIN | Find a user by email (in the body, never the URL): id, status, roles |
| `PUT /api/v1/admin/users/{id}/roles/{role}` | ADMIN | Grant a staff role (ADMIN, OPERATIONS, COMPLIANCE_OFFICER, SUPPORT_AGENT); idempotent, 204; 409 if the user isn't ACTIVE |
| `DELETE /api/v1/admin/users/{id}/roles/{role}` | ADMIN | Revoke a staff role; idempotent, 204; 409 for an admin's own ADMIN |

Staff register and verify like customers (they set their own password), then an ADMIN grants
roles; CUSTOMER is never granted or revoked this way. Roles live in the access token, so a change
applies from the user's next sign-in (tokens last 15 minutes). The first ADMIN is the address in
`security.bootstrap-admin.email` (`BOOTSTRAP_ADMIN_EMAIL`), granted when it is verified or at
startup; unset it afterwards.

**Publishes** on `auth.user-events.v1`: `UserRegistered.v1`.
**Consumes:** nothing.

## Customer Service (8082, `customer-db`)

**Owns:** customer profiles, KYC decisions (append-only), known users (read model).

| Endpoint | Who | Purpose |
|---|---|---|
| `POST /api/v1/customers` | CUSTOMER | Create own profile (PENDING_KYC); one per user |
| `GET /api/v1/customers/me` | CUSTOMER | Own profile |
| `GET /api/v1/compliance/customers/pending-kyc` | COMPLIANCE_OFFICER | Review queue, oldest first |
| `POST /api/v1/compliance/customers/{id}/kyc-approval` | COMPLIANCE_OFFICER | Approve KYC → ACTIVE (idempotent) |

**Publishes** on `customer.events.v1`: `CustomerVerified.v1`.
**Consumes:** `auth.user-events.v1` (`UserRegistered.v1`).

## Account Service (8083, `account-db`)

**Owns:** accounts (lifecycle, 12-digit numbers with MOD 97-10 check digits), eligible customers
(read model), funds holds, the balance projection of the ledger.

| Endpoint | Who | Purpose |
|---|---|---|
| `POST /api/v1/accounts` | CUSTOMER, KYC-verified | Open an account (Idempotency-Key); not verified → 403 `urn:novabank:problem:customer-not-verified`. The account is PENDING until the ledger confirms it (`LedgerAccountOpened.v1`), then ACTIVE; holds on a PENDING account are refused (`ACCOUNT_NOT_ACTIVE` / `BENEFICIARY_NOT_ACTIVE`) |
| `GET /api/v1/accounts` | CUSTOMER | Own accounts |
| `GET /api/v1/accounts/{id}` | CUSTOMER | Own account |
| `GET /api/v1/accounts/{id}/balance` | CUSTOMER | Ledger balance, held, available (= ledger − held) |

**Takes commands** on `account.commands.v1`: `HoldFunds.v1`, `ReleaseHold.v1`.
**Publishes** on `account.events.v1`: `AccountOpened.v1`, `FundsHeld.v1`, `FundsHoldFailed.v1`,
`HoldReleased.v1`, `BalancesUpdated.v1`.
**Consumes:** `customer.events.v1` (`CustomerVerified.v1`), `ledger.events.v1`
(`LedgerTransactionPosted.v1`, `LedgerAccountOpened.v1`, `LedgerBalanceSnapshot.v1`: drift check of
the balance projection, metric `novabank_projection_check_accounts_total{result}`).

## Ledger Service (8084, `ledger-db`)

**Owns:** the financial source of truth (ADR-001): ledger accounts, immutable double-entry
transactions and entries, final refusals. Customer accounts are liabilities; a `SETTLEMENT` asset
account exists per currency.

| Endpoint | Who | Purpose |
|---|---|---|
| `GET /api/v1/ledger/accounts/{id}` | OPERATIONS, ADMIN | Balance and last sequence |
| `GET /api/v1/ledger/accounts/{id}/entries` | OPERATIONS, ADMIN | Entries, newest first |
| `GET /api/v1/ledger/reconciliation` | OPERATIONS, ADMIN | Cached balances = entries; debits = credits per currency |

Read-only: money moves only through commands. The database rejects updates and deletes of ledger
rows and refuses to commit an unbalanced transaction.

**Takes commands** on `ledger.commands.v1`: `PostLedgerTransaction.v1`.
**Publishes** on `ledger.events.v1`: `LedgerTransactionPosted.v1`, `LedgerPostingFailed.v1`,
`LedgerAccountOpened.v1` (after creating a customer account from `AccountOpened.v1`).
Every 15 minutes it also publishes `LedgerBalanceSnapshot.v1` (customer balances and sequences).
**Consumes:** `account.events.v1` (`AccountOpened.v1`: creates the ledger account).

## Payment Service (8085, `payment-db`)

**Owns:** payments and their saga state (ADR-002). Orchestrates; never moves money itself.

| Endpoint | Who | Purpose |
|---|---|---|
| `POST /api/v1/payments` | CUSTOMER | Transfer from an own account to an account number; 202 (Idempotency-Key) |
| `GET /api/v1/payments`, `GET /api/v1/payments/{id}` | CUSTOMER | Own payments |
| `POST /api/v1/admin/deposits` | OPERATIONS, ADMIN | Money entering the bank (ADR-011); 202 (Idempotency-Key) |
| `GET /api/v1/admin/payments/{id}` | OPERATIONS, ADMIN | Any payment |

**Sends commands** to `account.commands.v1` and `ledger.commands.v1`.
**Publishes** on `payment.events.v1`: `PaymentCompleted.v1`, `PaymentFailed.v1`.
**Consumes:** `account.events.v1` and `ledger.events.v1` (saga replies).

---

## Not built yet

Notification and audit services (consumers of `payment.events.v1` and the other event topics),
refresh tokens. See the platform roadmap.
