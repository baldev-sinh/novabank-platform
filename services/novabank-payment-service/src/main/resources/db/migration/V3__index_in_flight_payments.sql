-- PaymentMetrics counts in-flight payments every 15 s (7.7). Finished payments are almost all
-- rows and are never in-flight again, so a partial index keeps that query small forever.
CREATE INDEX idx_payments_in_flight ON payments (created_at)
    WHERE status NOT IN ('COMPLETED', 'FAILED');
