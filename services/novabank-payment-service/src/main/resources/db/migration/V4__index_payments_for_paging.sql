-- A customer's payments are listed newest first in keyset pages (9.4): WHERE requested_by = ? AND
-- (created_at, payment id) < (cursor) ORDER BY created_at DESC, id DESC. With the id in the
-- index, every page is one index range scan and ties on created_at still page deterministically.
CREATE INDEX idx_payments_requested_by_page ON payments (requested_by, created_at DESC, id DESC);
DROP INDEX idx_payments_requested_by;
