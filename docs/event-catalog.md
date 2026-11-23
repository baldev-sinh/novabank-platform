# NovaBank Event Catalog

Every message exchanged over Kafka: events (facts, past tense) and commands (requests to the
service that owns the topic). Formats below are the contracts; changing one incompatibly means a
new version (`.v2`).

---

## Message envelope

| Part | Content |
|---|---|
| Header `eventId` | UUID, unique per message. Consumers deduplicate on it (processed-events registry). |
| Header `eventType` | Versioned name, e.g. `FundsHeld.v1`. Consumers skip types they don't know. |
| Header `traceparent` | W3C Trace Context of the producer span. Captured when the outbox row is written (`outbox_events.trace_headers`) and re-emitted by the relay, so one trace follows a request across every service. Consumers continue it (`spring.kafka.listener.observation-enabled`); dead-letter copies keep it. Optional: a missing header just starts a new trace. |
| Key | The aggregate the message is about: messages with one key stay in order on a topic. |
| Value | JSON. Amounts are decimal strings with an ISO 4217 code: `"amount":"125.50","currency":"INR"` (ADR-009). |

Rules:

- Published through the transactional outbox, at least once. Consumers must be idempotent.
- Consumers ignore unknown JSON fields (tolerant reader).
- Events carry ids, not personal data (no names, dates of birth, phone numbers).
- A message that can't be read goes to `<topic>.<consumer group>-dlt` (one per consuming service, so a replay reaches only the service that failed) without retries; other failures are retried
  with backoff (1 s, 2 s, 4 s) first. Business refusals are normal reply events.
- Topics are created by their owner with explicit partitions (3) and replication; broker
  auto-creation is off. Each consumer creates the dead-letter topics for what it reads.

## Topics

| Topic | Owner | Key | Messages |
|---|---|---|---|
| `auth.user-events.v1` | auth | userId | UserRegistered, StaffRoleGranted, StaffRoleRevoked |
| `customer.events.v1` | customer | customerId | CustomerVerified |
| `account.events.v1` | account | accountId (AccountOpened), paymentId (saga replies) | AccountOpened, FundsHeld, FundsHoldFailed, HoldReleased, BalancesUpdated |
| `account.commands.v1` | account | paymentId | HoldFunds, ReleaseHold |
| `ledger.commands.v1` | ledger | paymentId | PostLedgerTransaction |
| `ledger.events.v1` | ledger | paymentId (saga replies), accountId (LedgerAccountOpened) | LedgerTransactionPosted, LedgerPostingFailed, LedgerAccountOpened, LedgerBalanceSnapshot |
| `payment.events.v1` | payment | paymentId | PaymentCompleted, PaymentFailed |

The receiver of a command owns its command topic: it defines the contract it accepts.

---

## Identity and customers

**UserRegistered.v1** (auth → customer): `eventId, userId, email, occurredAt`.

**StaffRoleGranted.v1 / StaffRoleRevoked.v1** (auth → audit): `eventId, userId, role, changedBy,
granted, occurredAt`. An administrator (`changedBy`) changed a staff role; published only for a
real change (granting a role the user already has publishes nothing).

The audit service records every event on every topic in this catalog (except
`LedgerBalanceSnapshot.v1`), so a new event type is audited without any change there.

**CustomerVerified.v1** (customer → account): `eventId, customerId, userId, verifiedBy, occurredAt`.
The customer passed KYC and may open accounts.

## Accounts

**AccountOpened.v1** (account → ledger): `eventId, accountId, accountNumber, customerId,
ownerUserId, type, currency, occurredAt`. The ledger creates the matching ledger account.

**LedgerAccountOpened.v1** (ledger → account): `eventId, accountId, currency, openedAt`. The
ledger now holds the account; the account service moves it from PENDING to ACTIVE, and only then
can money move to or from it. Keyed by accountId.

**LedgerBalanceSnapshot.v1** (ledger → account): `eventId, snapshotId, takenAt, part,
accounts[]` with `accountId, balance, currency, sequence` per customer account, at most
`ledger.snapshot.batch-size` (500) per part. Every `ledger.snapshot.interval` (15 min), one ledger
instance (advisory lock) publishes the balance and last entry sequence of every customer account.
The account service compares each with its projection: same sequence with a different balance is
drift (`novabank_projection_check_accounts_total{result="drift"}`, WARN log with the account ids);
a different sequence is only timing. Keyed by snapshotId. Read-only, so redelivery is harmless.

## Payment saga

**HoldFunds.v1** (payment → account, command): `paymentId, accountId, requestedBy,
beneficiaryAccountNumber, amount, currency`. Reserve funds; also checks the beneficiary.

**FundsHeld.v1** (account → payment): `paymentId, holdId, accountId, beneficiaryAccountId, amount,
currency, occurredAt`.

**FundsHoldFailed.v1** (account → payment): `paymentId, reason, occurredAt`. Reasons:
`ACCOUNT_NOT_FOUND, NOT_ACCOUNT_OWNER, ACCOUNT_NOT_ACTIVE, BENEFICIARY_NOT_FOUND,
BENEFICIARY_NOT_ACTIVE, SAME_ACCOUNT, CURRENCY_MISMATCH, INSUFFICIENT_FUNDS`.

**ReleaseHold.v1** (payment → account, command): `paymentId`. Compensation.

**HoldReleased.v1** (account → payment): `paymentId, outcome, occurredAt`. Outcome: `RELEASED`,
`NO_HOLD` (nothing was held), `ALREADY_CAPTURED` (should never happen; flagged for operations).

**PostLedgerTransaction.v1** (payment → ledger, command): `paymentId, type (DEPOSIT | TRANSFER),
postings[]` with each posting `accountId` *or* `systemAccount` (`SETTLEMENT`), `direction (DEBIT |
CREDIT), amount, currency`. Must balance per currency.

**LedgerTransactionPosted.v1** (ledger → payment, account): `paymentId, transactionId, type,
postedAt, entries[]` with each entry `accountId, direction, amount, currency, balanceAfter,
sequence`. `sequence` is the account's gapless entry number: the balance projection applies only
higher sequences, so late or repeated events can't roll a balance back.

**LedgerPostingFailed.v1** (ledger → payment): `paymentId, reason, occurredAt`. Reasons:
`UNKNOWN_ACCOUNT, CURRENCY_MISMATCH, INSUFFICIENT_FUNDS, INVALID_POSTINGS`.

**BalancesUpdated.v1** (account → payment): `paymentId, transactionId, accountIds[], occurredAt`.
The projection applied the posting and captured the hold.

**PaymentCompleted.v1 / PaymentFailed.v1** (payment → notification, audit): `paymentId, type,
requestedBy, fromAccountId, toAccountId, amount, currency, status, failureReason,
ledgerTransactionId, occurredAt`.

### Final answers

The account and ledger services answer **the same way for the same paymentId**, however often a
command is sent (one hold per payment, one ledger transaction per payment, refusals stored). That
is what makes the payment saga's re-sends after a timeout safe.
