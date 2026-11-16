# NovaBank Payment Lifecycle

How a payment moves money without distributed transactions: an orchestrated saga (ADR-002) over
Kafka commands and replies (ADR-008). The payment service owns the payment's status; the account
service owns holds and balances; the ledger owns the money (ADR-001).

---

## Transfer

```text
Customer: POST /api/v1/payments (Idempotency-Key)      → 202, status INITIATED
   │  HoldFunds ─────────────────────────────▶ account   (checks sender and beneficiary,
   │                                                       reserves the amount)
   │◀──────────────────────────────── FundsHeld
FUNDS_HELD
   │  PostLedgerTransaction ─────────────────▶ ledger    (debit sender, credit beneficiary)
   │◀──────────────────── LedgerTransactionPosted ──────▶ account (projection: balances,
LEDGER_POSTED                                                       captures the hold)
   │◀──────────────────────────────── BalancesUpdated
COMPLETED  → PaymentCompleted
```

**Failures**

| What happened | Path | Money |
|---|---|---|
| Hold refused (e.g. `INSUFFICIENT_FUNDS`, `NOT_ACCOUNT_OWNER`, `BENEFICIARY_NOT_FOUND`) | INITIATED → FAILED | nothing reserved, nothing moved |
| Ledger refused after the hold | FUNDS_HELD → COMPENSATING → (ReleaseHold / HoldReleased) → FAILED | hold released, nothing moved |

Failed transfers are compensated, never deleted (ADR-005).

## Deposit (operations staff, ADR-011)

`POST /api/v1/admin/deposits` → INITIATED → PostLedgerTransaction (debit `SETTLEMENT`, credit the
account) → LEDGER_POSTED → BalancesUpdated → COMPLETED. A refusal (e.g. `UNKNOWN_ACCOUNT`) goes to
FAILED. No hold: money is entering the bank.

## States

| State | Meaning | Waiting for |
|---|---|---|
| INITIATED | Accepted, first command sent | FundsHeld / FundsHoldFailed (transfer), ledger reply (deposit) |
| FUNDS_HELD | Amount reserved on the sender | ledger reply |
| LEDGER_POSTED | Money moved in the ledger | BalancesUpdated |
| COMPENSATING | Ledger refused; releasing the hold | HoldReleased |
| COMPLETED | Done | – |
| FAILED | Done, nothing moved; `failureReason` says why | – |

---

## Guarantees and how they are achieved

| Guarantee | How |
|---|---|
| A retried request never pays twice | `Idempotency-Key` stored with the result in the same transaction as the payment |
| Funds can't be spent twice | Holds placed under the sender's balance-row lock; available = ledger balance − active holds |
| Balance and hold never double-count | The projection lowers the balance and captures the hold in one transaction |
| No overdraft in the ledger | Accounts locked in id order; customer balances can't go below zero (code + CHECK constraint) |
| Ledger is balanced and immutable | Domain check + deferred constraint trigger at commit; triggers reject UPDATE/DELETE/TRUNCATE |
| A state change and its message can't diverge | Transactional outbox in every service |
| Redelivered messages are harmless | Processed-events registry; the payment state machine ignores replies that don't fit |
| Replies may arrive in any order | They come from two topics; e.g. BalancesUpdated before LedgerTransactionPosted still completes |
| Concurrent replies for one payment | The payment row is locked (`FOR UPDATE`) for each reply |

## Timeouts and recovery

No reply within `payments.step-timeout` (default 30 s) → a sweeper re-sends the current step's
command, with exponential backoff, from any instance (`FOR UPDATE SKIP LOCKED`). Re-sending is
safe because the account and ledger services give **the same final answer for the same
paymentId**. The saga never assumes failure on silence: once funds are held, only the ledger knows
whether money moved, so it is asked again. After `payments.max-attempts` the payment is flagged
`needs_attention` for operations staff and retried less often.

## Not covered yet

Scheduled and recurring payments, cancellation, reversals of completed payments, multi-currency
and FX, fraud and AML screening, payment limits beyond a single maximum amount, notifications.
