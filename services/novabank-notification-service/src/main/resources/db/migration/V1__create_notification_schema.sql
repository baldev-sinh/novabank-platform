-- Notification service (7.6, Decision AI).

-- Idempotent consumer: each event's effect commits together with its id here.
CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Own copy of what the emails need (from UserRegistered and AccountOpened): no calls to other
-- services when sending.
CREATE TABLE known_users (
    user_id       UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE known_accounts (
    account_id     UUID        PRIMARY KEY,
    account_number VARCHAR(34) NOT NULL,
    owner_user_id  UUID        NOT NULL,
    currency       CHAR(3)     NOT NULL
);

-- One row per email to send. The recipient is resolved when sending, so a payment event that
-- arrives before the account or user it refers to just waits for them.
CREATE TABLE notifications (
    id                   UUID        PRIMARY KEY,
    event_id             UUID        NOT NULL,
    kind                 VARCHAR(30) NOT NULL,
    recipient_user_id    UUID,
    recipient_account_id UUID,         -- "money received": whoever owns this account
    details              JSONB       NOT NULL,
    status               VARCHAR(10) NOT NULL,  -- PENDING, SENT, FAILED
    attempts             INT         NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error           VARCHAR(500),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at              TIMESTAMP WITH TIME ZONE,
    -- At most one email of each kind per event, however often the event is delivered.
    CONSTRAINT uk_notifications_event_kind UNIQUE (event_id, kind),
    CONSTRAINT ck_notifications_recipient
        CHECK ((recipient_user_id IS NULL) <> (recipient_account_id IS NULL)),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- The dispatcher only ever scans due, pending rows.
CREATE INDEX idx_notifications_due ON notifications (next_attempt_at) WHERE status = 'PENDING';
