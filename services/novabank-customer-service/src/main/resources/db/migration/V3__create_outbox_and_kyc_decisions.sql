-- Transactional outbox (schema expected by novabank-platform-starter's OutboxWriter/OutboxRelay).
CREATE TABLE outbox_events (
    id             UUID         PRIMARY KEY,   -- also the event id consumers deduplicate on
    position       BIGINT       GENERATED ALWAYS AS IDENTITY,
    topic          VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,      -- Kafka key: keeps one customer's events in order
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (position)
    WHERE published_at IS NULL;

-- Append-only record of who decided a customer's KYC, and when (regulatory audit trail).
CREATE TABLE kyc_decisions (
    id          UUID        PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES customers (id),
    decision    VARCHAR(20) NOT NULL,
    decided_by  UUID        NOT NULL,      -- the staff user's id (JWT subject)
    decided_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_kyc_decisions_decision CHECK (decision IN ('APPROVED'))
);

CREATE INDEX idx_kyc_decisions_customer ON kyc_decisions (customer_id);

-- The compliance review queue reads pending customers oldest first.
CREATE INDEX idx_customers_pending_kyc
    ON customers (created_at)
    WHERE status = 'PENDING_KYC';
