-- Refresh tokens (7.1, Decision AE): opaque, only the SHA-256 hash is stored. Every refresh marks
-- the token used and issues the next one in the same family; presenting a used token again revokes
-- the family (RFC 9700 section 4.14, RFC 10017).
CREATE TABLE refresh_tokens (
    id                UUID        PRIMARY KEY,
    family_id         UUID        NOT NULL,   -- one sign-in; all its rotations share it
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash        VARCHAR(64) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,   -- idle timeout of this token
    family_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,   -- absolute limit of the sign-in
    used_at           TIMESTAMP WITH TIME ZONE,
    revoked_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

-- Revoking a family, and the purge of long-expired sign-ins.
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_family_expires_at ON refresh_tokens (family_expires_at);
