-- Failed sign-ins per account (account lockout, Decision S). Kept out of the users table so the
-- counter is updated atomically and never bumps the user's optimistic-lock version.
CREATE TABLE login_attempts (
    user_id        UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    failed_count   INTEGER     NOT NULL,
    last_failed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until   TIMESTAMP WITH TIME ZONE
);
