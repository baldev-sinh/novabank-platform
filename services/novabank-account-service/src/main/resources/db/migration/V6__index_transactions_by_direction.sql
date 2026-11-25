-- "Money in" / "Money out" (9.3): WHERE account_id = ? AND direction = ? AND sequence < ?
-- ORDER BY sequence DESC. Without this index a filtered page would scan past every line of the
-- other direction; with it, each page is one index range scan, like the unfiltered ones (PK).
CREATE INDEX idx_account_transactions_direction
    ON account_transactions (account_id, direction, sequence DESC);
