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
| Key | The aggregate the message is about: messages with one key stay in order on a topic. |
| Value | JSON. Amounts are decimal strings with an ISO 4217 code: `"amount":"125.50","currency":"INR"` (ADR-009). |

Rules:

- Published through the transactional outbox, at least once. Consumers must be idempotent.
- Consumers ignore unknown JSON fields (tolerant reader).
- Events carry ids, not personal data (no names, dates of birth, phone numbers).
- A message that can't be read goes to `<topic>-dlt` without retries; other failures are retried
  with backoff (1 s, 2 s, 4 s) first. Business refusals are normal reply events.
- Topics are created by their owner with explicit partitions (3) and replication; broker
  auto-creation is off. Each consumer creates the dead-letter topics for what it reads.

## Topics

| Topic | Owner | Key | Messages |
|---|---|---|---|
| `auth.user-events.v1` | auth | userId | UserRegistered |
| `customer.events.v1` | customer | customerId | CustomerVerified |
| `account.events.v1` | account | accountId (AccountOpened), paymentId (saga replies) | AccountOpened, FundsHeld, FundsHoldFailed, HoldReleased, BalancesUpdated |
| `account.commands.v1` | account | paymentId | HoldFunds, ReleaseHold |
| `ledger.commands.v1` | ledger | paymentId | PostLedgerTransaction |
| `ledger.events.v1` | ledger | paymentId | LedgerTransactionPosted, LedgerPostingFailed |
| `payment.events.v1` | payment | paymentId | PaymentCompleted, PaymentFailed |

The receiver of a command owns its command topic: it defines the contract it accepts.

---

## Identity and customers

**UserRegistered.v1** (auth → customer): `eventId, userId, email, occurredAt`.

**CustomerVerified.v1** (customer → account): `eventId, customerId, userId, verifiedBy, occurredAt`.
The customer passed KYC and may open accounts.

## Accounts

**AccountOpened.v1** (account → ledger): `eventId, accountId, accountNumber, customerId,
ownerUserId, type, currency, occurredAt`. The ledger creates the matching ledger account.

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
