-- The payment's reference (the narrative on a statement line), carried to the account service so
-- customers see it in their transaction history (8.1). Nullable: references are optional and
-- earlier transactions have none. Adding a column is DDL, not an UPDATE, so the immutability
-- triggers don't apply.
ALTER TABLE ledger_transactions ADD COLUMN reference VARCHAR(140);
