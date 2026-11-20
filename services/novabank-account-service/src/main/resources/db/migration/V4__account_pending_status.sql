-- New accounts start PENDING until the ledger confirms its side (LedgerAccountOpened, 6.4).
-- Existing rows keep their status: their ledger accounts were created when they were opened.
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_status;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_status
    CHECK (status IN ('PENDING', 'ACTIVE', 'FROZEN', 'CLOSED'));
