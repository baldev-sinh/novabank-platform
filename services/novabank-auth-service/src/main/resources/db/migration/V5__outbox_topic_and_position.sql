-- The shared platform outbox publishes each row to its own topic and in insert order.
ALTER TABLE outbox_events ADD COLUMN topic VARCHAR(255);
-- Every row so far was a user event.
UPDATE outbox_events SET topic = 'auth.user-events.v1';
ALTER TABLE outbox_events ALTER COLUMN topic SET NOT NULL;

-- created_at can tie within one transaction; an identity column gives a strict insert order.
-- Existing rows are numbered in created_at order.
ALTER TABLE outbox_events ADD COLUMN position BIGINT;
UPDATE outbox_events o
SET position = ordered.n
FROM (SELECT id, row_number() OVER (ORDER BY created_at, id) AS n FROM outbox_events) ordered
WHERE o.id = ordered.id;
ALTER TABLE outbox_events ALTER COLUMN position SET NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN position ADD GENERATED ALWAYS AS IDENTITY;
SELECT setval(pg_get_serial_sequence('outbox_events', 'position'),
              COALESCE((SELECT max(position) FROM outbox_events), 0) + 1, false);

DROP INDEX idx_outbox_events_unpublished;
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (position)
    WHERE published_at IS NULL;
