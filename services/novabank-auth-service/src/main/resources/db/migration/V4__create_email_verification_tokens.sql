-- One-time email verification tokens. Only the SHA-256 hash is stored, so a database leak
-- doesn't let anyone verify an address they don't own.
CREATE TABLE email_verification_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_email_verification_tokens_hash UNIQUE (token_hash)
);

-- Resend looks up a user's latest token and removes their unused ones.
CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id, created_at);
