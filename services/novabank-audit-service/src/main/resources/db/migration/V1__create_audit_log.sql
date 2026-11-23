-- Append-only audit log (7.4, Decision AH). Each entry is chained to the one before it:
-- hash = HMAC-SHA256(key, prev_hash || entry). The key is not in the database, so an edit,
-- insert or deletion can't be covered up by recomputing hashes; GET /api/v1/audit/verify finds it.
CREATE TABLE audit_entries (
    seq          BIGINT       PRIMARY KEY,             -- 1, 2, 3 ... with no gaps
    event_id     UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    topic        VARCHAR(200) NOT NULL,
    record_key   VARCHAR(200),                         -- the aggregate: userId, accountId, paymentId ...
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,    -- the Kafka record's timestamp
    recorded_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Exactly as received. TEXT, not JSONB: JSONB reorders keys and drops whitespace, which would
    -- change the bytes the hash was computed over.
    payload      TEXT         NOT NULL,
    prev_hash    BYTEA        NOT NULL,
    hash         BYTEA        NOT NULL,
    CONSTRAINT uk_audit_entries_event_id UNIQUE (event_id)  -- a redelivered event is recorded once
);

-- Reads: everything about one aggregate, or one event type, newest first.
CREATE INDEX idx_audit_entries_key ON audit_entries (record_key, seq DESC);
CREATE INDEX idx_audit_entries_type ON audit_entries (event_type, seq DESC);

-- The end of the chain. Every append locks this one row, so appends are serialised and each
-- entry's prev_hash is the hash of the entry before it.
CREATE TABLE audit_chain_head (
    id         SMALLINT PRIMARY KEY CHECK (id = 1),
    last_seq   BIGINT   NOT NULL,
    last_hash  BYTEA    NOT NULL
);
INSERT INTO audit_chain_head (id, last_seq, last_hash) VALUES (1, 0, decode(repeat('00', 32), 'hex'));

-- History can't be changed through SQL by accident; the chain catches anyone who disables this.
CREATE FUNCTION audit_entries_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit log is append-only (% refused)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entries_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION audit_entries_append_only();

CREATE TRIGGER audit_entries_no_truncate
    BEFORE TRUNCATE ON audit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION audit_entries_append_only();

-- The head row is updated on every append, but never removed.
CREATE TRIGGER audit_chain_head_no_delete
    BEFORE DELETE OR TRUNCATE ON audit_chain_head
    FOR EACH STATEMENT EXECUTE FUNCTION audit_entries_append_only();
