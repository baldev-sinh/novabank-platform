-- The customer's transaction history (8.1): one row per ledger entry on one of this service's
-- accounts, written from LedgerTransactionPosted in the same transaction as the balance projection.
-- A read model: the ledger stays the record; this copy adds what a statement needs and the ledger
-- doesn't know (the counterparty account) and can be rebuilt from the ledger's events.
CREATE TABLE account_transactions (
    account_id              UUID          NOT NULL REFERENCES accounts (id),
    -- The ledger's gapless entry number for this account: the row's identity and its order.
    sequence                BIGINT        NOT NULL,
    transaction_id          UUID          NOT NULL,
    payment_id              UUID          NOT NULL,
    type                    VARCHAR(20)   NOT NULL,
    direction               VARCHAR(6)    NOT NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    balance_after           NUMERIC(19,4) NOT NULL,
    -- The other customer account of a transfer; NULL for deposits (the bank's settlement account).
    counterparty_account_id UUID          REFERENCES accounts (id),
    reference               VARCHAR(140),
    posted_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Also the index for newest-first keyset pages: WHERE account_id = ? AND sequence < ?
    -- ORDER BY sequence DESC.
    PRIMARY KEY (account_id, sequence),
    CONSTRAINT ck_account_transactions_type CHECK (type IN ('DEPOSIT', 'TRANSFER')),
    CONSTRAINT ck_account_transactions_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_account_transactions_amount_positive CHECK (amount > 0)
);
