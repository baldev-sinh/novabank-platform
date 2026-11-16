-- The bank's settlement account per currency (an ASSET: money the bank holds). Deposits debit it
-- and credit the customer's account (Decision N). Same currencies as the account service offers.
INSERT INTO ledger_accounts (id, kind, code, account_class, currency, balance, last_sequence, created_at)
SELECT gen_random_uuid(), 'SYSTEM', 'SETTLEMENT', 'ASSET', currency, 0, 0, now()
FROM unnest(ARRAY['INR', 'EUR', 'USD', 'GBP']) AS currency;
