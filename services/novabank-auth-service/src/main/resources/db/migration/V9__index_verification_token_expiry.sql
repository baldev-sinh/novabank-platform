-- The scheduled purge (1.13b) deletes tokens by expiry; without an index each batch scans the table.
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens (expires_at);
