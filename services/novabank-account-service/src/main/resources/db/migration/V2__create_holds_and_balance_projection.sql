-- Balance projection: what the ledger (source of truth, ADR-001) says each account holds. Created
-- on first use; updated from LedgerTransactionPosted events, and only forward: an entry is applied
-- only if its per-account ledger sequence is higher than the last one applied.
CREATE TABLE account_balances (
    account_id           UUID          PRIMARY KEY REFERENCES accounts (id),
    ledger_balance       NUMERIC(19,4) NOT NULL,
    last_ledger_sequence BIGINT        NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Funds reserved for payments in progress (ADR-006). available = ledger_balance - ACTIVE holds.
CREATE TABLE account_holds (
    id                     UUID          PRIMARY KEY,
    payment_id             UUID          NOT NULL,
    account_id             UUID          NOT NULL REFERENCES accounts (id),
    beneficiary_account_id UUID          NOT NULL REFERENCES accounts (id),
    amount                 NUMERIC(19,4) NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- One hold per payment: a re-sent HoldFunds can never reserve twice.
    CONSTRAINT uk_account_holds_payment UNIQUE (payment_id),
    CONSTRAINT ck_account_holds_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_account_holds_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CAPTURED'))
);

-- "Sum of active holds" runs on every hold check.
CREATE INDEX idx_account_holds_active ON account_holds (account_id) WHERE status = 'ACTIVE';

-- Final "no" per payment, so a re-sent HoldFunds gets the same answer.
CREATE TABLE hold_rejections (
    payment_id  UUID        PRIMARY KEY,
    reason      VARCHAR(30) NOT NULL,
    rejected_at TIMESTAMP WITH TIME ZONE NOT NULL
);
