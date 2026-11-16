-- Payments and their saga state (ADR-002). The payment service owns the status; the money is in
-- the ledger, the holds in the account service.
CREATE TABLE payments (
    id                    UUID          PRIMARY KEY,
    type                  VARCHAR(20)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    requested_by          UUID          NOT NULL,
    from_account_id       UUID,
    to_account_number     VARCHAR(12),
    to_account_id         UUID,
    amount                NUMERIC(19,4) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    reference             VARCHAR(140),
    failure_reason        VARCHAR(50),
    hold_id               UUID,
    ledger_transaction_id UUID,
    -- Timeouts: when the current step is due for a re-send, and how often it was re-sent.
    attempts              INT           NOT NULL,
    next_attempt_at       TIMESTAMP WITH TIME ZONE,
    needs_attention       BOOLEAN       NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_payments_type CHECK (type IN ('TRANSFER', 'DEPOSIT')),
    CONSTRAINT ck_payments_status CHECK (status IN
        ('INITIATED', 'FUNDS_HELD', 'LEDGER_POSTED', 'COMPENSATING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_shape CHECK (
        (type = 'TRANSFER' AND from_account_id IS NOT NULL AND to_account_number IS NOT NULL)
        OR (type = 'DEPOSIT' AND from_account_id IS NULL AND to_account_id IS NOT NULL)),
    -- Finished payments have no pending step.
    CONSTRAINT ck_payments_terminal_no_step CHECK (
        status NOT IN ('COMPLETED', 'FAILED') OR next_attempt_at IS NULL)
);

-- "My payments", newest first.
CREATE INDEX idx_payments_requested_by ON payments (requested_by, created_at DESC);
-- The sweeper scans only payments with a pending step.
CREATE INDEX idx_payments_due ON payments (next_attempt_at) WHERE next_attempt_at IS NOT NULL;

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

-- Idempotency-Key store (novabank-platform-starter IdempotentRequests).
CREATE TABLE idempotency_keys (
    owner_id        UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (owner_id, idempotency_key)
);
