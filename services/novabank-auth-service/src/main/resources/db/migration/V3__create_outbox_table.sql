-- Transactional outbox: events are written in the same transaction as the business change,
-- then published to Kafka by OutboxRelay. A row is never lost even if Kafka is down.
CREATE TABLE outbox_events (
    id             UUID         PRIMARY KEY,   -- also the event id consumers deduplicate on
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,      -- Kafka key: keeps one user's events in order
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at   TIMESTAMP WITH TIME ZONE
);

-- The relay only ever scans unpublished rows, oldest first; keep that index small.
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
