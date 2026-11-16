-- The ledger: NovaBank's financial source of truth (ADR-001). Double-entry: every transaction's
-- debits equal its credits per currency. Transactions and entries are never changed or deleted;
-- corrections are new transactions (ADR-005).

CREATE TABLE ledger_accounts (
    id            UUID          PRIMARY KEY,   -- for CUSTOMER accounts: the account service's id
    kind          VARCHAR(20)   NOT NULL,
    code          VARCHAR(50),                 -- SYSTEM accounts only, e.g. SETTLEMENT
    account_class VARCHAR(20)   NOT NULL,
    currency      VARCHAR(3)    NOT NULL,
    -- Cached running balance in the account's normal direction; reconciliation checks it
    -- against the entries.
    balance       NUMERIC(19,4) NOT NULL,
    last_sequence BIGINT        NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ledger_accounts_kind CHECK (kind IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT ck_ledger_accounts_class CHECK (account_class IN ('ASSET', 'LIABILITY')),
    CONSTRAINT ck_ledger_accounts_code CHECK ((kind = 'SYSTEM') = (code IS NOT NULL)),
    -- Defence in depth for "no overdrafts": the service checks first, the database last.
    CONSTRAINT ck_ledger_accounts_customer_not_negative CHECK (kind <> 'CUSTOMER' OR balance >= 0)
);

CREATE UNIQUE INDEX uk_ledger_accounts_system_code ON ledger_accounts (code, currency)
    WHERE kind = 'SYSTEM';

CREATE TABLE ledger_transactions (
    id         UUID        PRIMARY KEY,
    payment_id UUID        NOT NULL,
    type       VARCHAR(20) NOT NULL,
    posted_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    -- One posting per payment, ever: the business-level idempotency key.
    CONSTRAINT uk_ledger_transactions_payment UNIQUE (payment_id),
    CONSTRAINT ck_ledger_transactions_type CHECK (type IN ('DEPOSIT', 'TRANSFER'))
);

CREATE TABLE ledger_entries (
    id               UUID          PRIMARY KEY,
    transaction_id   UUID          NOT NULL REFERENCES ledger_transactions (id),
    account_id       UUID          NOT NULL REFERENCES ledger_accounts (id),
    direction        VARCHAR(6)    NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    balance_after    NUMERIC(19,4) NOT NULL,
    account_sequence BIGINT        NOT NULL,
    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0),
    -- Each account's entries are numbered 1, 2, 3, ... without gaps or repeats.
    CONSTRAINT uk_ledger_entries_account_sequence UNIQUE (account_id, account_sequence)
);

CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);

-- Final "no" for a payment, so a re-sent command gets the same answer.
CREATE TABLE rejected_postings (
    payment_id  UUID        PRIMARY KEY,
    reason      VARCHAR(30) NOT NULL,
    rejected_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Immutability, enforced by the database itself (not just by the code).
CREATE FUNCTION reject_ledger_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Ledger records are immutable: % on % is not allowed', TG_OP, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_ledger_transactions_immutable
    BEFORE UPDATE OR DELETE ON ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();
CREATE TRIGGER trg_ledger_transactions_no_truncate
    BEFORE TRUNCATE ON ledger_transactions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();
CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();
CREATE TRIGGER trg_ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();

-- Double entry, checked at COMMIT (deferred): a transaction whose entries don't sum to zero per
-- currency can't be committed, whatever the code does.
CREATE FUNCTION check_ledger_transaction_balanced() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM ledger_entries
        WHERE transaction_id = NEW.transaction_id
        GROUP BY currency
        HAVING sum(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END) <> 0
    ) THEN
        RAISE EXCEPTION 'Ledger transaction % is not balanced', NEW.transaction_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_ledger_transaction_balanced();

-- Consumer idempotency (novabank-platform-starter ProcessedEvents).
CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Transactional outbox (novabank-platform-starter OutboxWriter/OutboxRelay).
CREATE TABLE outbox_events (
    id             UUID         PRIMARY KEY,
    position       BIGINT       GENERATED ALWAYS AS IDENTITY,
    topic          VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (position)
    WHERE published_at IS NULL;
