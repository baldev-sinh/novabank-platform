# NovaBank Event Catalog

## Payment Events

### PaymentInitiated

Raised when a customer requests a money transfer.

Producer:

* Payment Service

Consumers:

* Payment Service

---

### AccountsValidated

Raised when sender and receiver accounts are verified.

Producer:

* Payment Service

Consumers:

* Payment Service

---

### FundsHeld

Raised when funds are successfully reserved on the sender account.

Producer:

* Account Service

Consumers:

* Payment Service

---

### HoldReleased

Raised when reserved funds are released.

Producer:

* Account Service

Consumers:

* Payment Service

---

### FundsHoldFailed

Raised when funds cannot be reserved.

Producer:

* Account Service

Consumers:

* Payment Service

---

### LedgerTransactionPosted

Raised when debit and credit entries are successfully posted.

Producer:

* Ledger Service

Consumers:

* Payment Service
* Account Service
* Audit Service

---

### LedgerPostingFailed

Raised when ledger posting fails.

Producer:

* Ledger Service

Consumers:

* Payment Service

---

### BalancesUpdated

Raised when balance projections are updated.

Producer:

* Account Service

Consumers:

* Payment Service

---

### PaymentCompleted

Raised when a payment workflow successfully completes.

Producer:

* Payment Service

Consumers:

* Notification Service
* Audit Service

---

### PaymentFailed

Raised when a payment workflow fails.

Producer:

* Payment Service

Consumers:

* Notification Service
* Audit Service
