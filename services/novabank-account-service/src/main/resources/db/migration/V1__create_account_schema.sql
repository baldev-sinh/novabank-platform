-- Customer bank accounts. Balances are not stored here: they are projections of the ledger
-- (ADR-001, ADR-007) and arrive in a later migration.
CREATE TABLE accounts (
    id             UUID        PRIMARY KEY,
    account_number VARCHAR(12) NOT NULL,
    owner_user_id  UUID        NOT NULL,
    customer_id    UUID        NOT NULL,
    type           VARCHAR(20) NOT NULL,
    currency       VARCHAR(3)  NOT NULL,
    status         VARCHAR(20) NOT NULL,
    opened_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    version        BIGINT      NOT NULL,
    CONSTRAINT uk_accounts_account_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_type CHECK (type IN ('CURRENT', 'SAVINGS')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

-- "My accounts" is the most common query.
CREATE INDEX idx_accounts_owner ON accounts (owner_user_id, opened_at);

-- Read model: customers who passed KYC (from CustomerVerified events).
CREATE TABLE eligible_customers (
    customer_id UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_eligible_customers_user_id UNIQUE (user_id)
);

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
