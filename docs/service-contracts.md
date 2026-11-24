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
  (processed-events registry), retries with backoff, then a `<topic>.<consumer group>-dlt` dead-letter topic.
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
| `POST /api/v1/auth/login` | public | Access token in the body; refresh token as an HttpOnly cookie; ACTIVE users only (unverified: 403 `urn:novabank:problem:email-not-verified`) |
| `GET /api/v1/auth/me` | signed in | Current user |
| `POST /api/v1/auth/refresh` | refresh cookie + `X-Requested-With` | New access token, rotated cookie; a reused (already rotated) token revokes the whole sign-in; 401 to sign in again |
| `POST /api/v1/auth/logout` | refresh cookie + `X-Requested-With` | Ends the sign-in, clears the cookie; 204, idempotent |
| `POST /oauth2/token` | registered service, `private_key_jwt` | Service token (`client_credentials`, OAuth 2.1): 5-minute JWT, `sub` = client id, `scope` (e.g. `accounts:read`), same issuer, audience and key as user tokens. The service signs an RFC 7523 assertion with its own key; auth checks it against the service's JWKS (`security.service-clients`) |
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
| `GET /api/v1/accounts/{id}/transactions?limit=&before=` | CUSTOMER | Transaction history, newest first: direction, amount, balance after, counterparty (last 4 digits), reference. Keyset pages: pass `nextBefore` as `before`; `limit` 1–100, default 25 |
| `GET /api/v1/internal/accounts/{id}` | service token with `accounts:read` | `accountId, status, currency` for other services (the payment service's deposit check); 404 if unknown. User tokens never carry scopes, so customers and staff get 403 |

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

## Audit Service (8086, `audit-db`)

**Owns:** the audit log: every business event, append-only and hash-chained (ADR in Decision AH of
the roadmap). Each entry stores the event exactly as received plus
`hash = HMAC-SHA256(key, prev_hash ‖ entry)`; the key comes from the secret store
(`AUDIT_CHAIN_KEY`), never the database. A trigger refuses UPDATE, DELETE and TRUNCATE.

| Endpoint | Who | Purpose |
|---|---|---|
| `GET /api/v1/audit/entries?key=&eventType=&from=&to=&before=&limit=` | COMPLIANCE_OFFICER, ADMIN | Newest first; `key` is the aggregate id (user, customer, account or payment); `nextBefore` pages |
| `GET /api/v1/audit/verify` | COMPLIANCE_OFFICER, ADMIN | Walks the chain: `valid`, `checkedEntries`, `headSeq`, `headHash`, `brokenAtSeq`, `problem` |

Also checked hourly; `novabank_audit_chain_valid` is 0 after a failed check (alert on it). Copying
`headSeq` + `headHash` somewhere else now and then anchors the chain, so even a removed tail shows.
**Consumes:** `auth.user-events.v1`, `customer.events.v1`, `account.events.v1`, `ledger.events.v1`,
`payment.events.v1`, every type except `LedgerBalanceSnapshot.v1`. Publishes nothing.

---

## Notification Service (8087, `notification-db`)

**Owns:** its own copy of user emails and account owners/numbers, and the emails to send
(Decision AI). No API: every path but the probes is refused.

| Email | To | When |
|---|---|---|
| Your NovaBank account is ready | the owner | `AccountActivated.v1` |
| Transfer sent | the sender (`requestedBy`) | `PaymentCompleted.v1`, type TRANSFER |
| Transfer not completed | the sender | `PaymentFailed.v1`, type TRANSFER (with the reason) |
| Money received | the owner of the credited account | `PaymentCompleted.v1`, TRANSFER or DEPOSIT |

Account numbers appear masked (`•••• 4821`). Each event produces at most one email of each kind
(unique per event and kind). A scheduled dispatcher claims due emails (`FOR UPDATE SKIP LOCKED`, a
2-minute lease), sends them outside any transaction and retries failures with backoff (30 s
doubling to 1 h, 10 attempts, then FAILED; metrics `novabank_notifications_sent` / `_failed` by
kind). The recipient is resolved when sending, so an event that arrives before the user or account
it refers to just waits. Events older than 24 h update the projection but send no email.
**Consumes:** `auth.user-events.v1` (`UserRegistered.v1`), `account.events.v1`
(`AccountOpened.v1`, `AccountActivated.v1`), `payment.events.v1` (`PaymentCompleted.v1`,
`PaymentFailed.v1`). Publishes nothing.

---

## Not built yet

See the platform roadmap.
