-- Bucket4j token buckets for per-IP rate limits (Decision T). Layout required by
-- PostgreSQLSelectForUpdateBasedProxyManager with a String key and an expiration policy:
-- https://bucket4j.com/8.22.0/toc.html#bucket4j-postgresql
CREATE TABLE rate_limit_buckets (
    id         VARCHAR(255) PRIMARY KEY,  -- "<rule>|<client ip>"
    state      BYTEA,
    expires_at BIGINT                     -- epoch millis, set by Bucket4j
);

CREATE INDEX idx_rate_limit_buckets_expires_at ON rate_limit_buckets (expires_at);
