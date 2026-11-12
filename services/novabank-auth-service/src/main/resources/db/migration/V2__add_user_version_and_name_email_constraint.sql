-- Optimistic locking for the User aggregate (JPA @Version).
-- Existing rows start at 0; every update increments it.
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Give the unique email constraint a stable, explicit name so the application can recognise
-- a duplicate-email violation reliably (it was auto-named users_email_key by PostgreSQL).
ALTER TABLE users RENAME CONSTRAINT users_email_key TO uk_users_email;
