-- Consumer idempotency: an event id is recorded in the same transaction as its effect.
-- A redelivered event hits the primary key and is skipped.
CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Read model of user accounts known from the auth service (UserRegistered events),
-- e.g. for KYC reminders to users who haven't created a customer profile yet.
CREATE TABLE known_users (
    user_id       UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL
);
