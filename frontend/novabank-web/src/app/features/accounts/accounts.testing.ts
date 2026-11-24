import { Payment } from '../payments/payments.api';
import { Account, Balance, Transaction } from './accounts.api';

/** Fixtures for specs. */
export const CURRENT: Account = {
  accountId: 'a-1',
  accountNumber: '502148369012',
  type: 'CURRENT',
  currency: 'INR',
  status: 'ACTIVE',
  openedAt: '2026-11-12T10:00:00Z',
};

export const SAVINGS: Account = {
  ...CURRENT,
  accountId: 'a-2',
  accountNumber: '502148364471',
  type: 'SAVINGS',
};

export function balanceOf(accountId: string, available: string, held = '0.00'): Balance {
  return {
    accountId,
    currency: 'INR',
    ledgerBalance: available,
    held,
    available,
    ledgerSequence: 1,
  };
}

export function line(sequence: number, overrides: Partial<Transaction> = {}): Transaction {
  return {
    sequence,
    paymentId: `pay-${sequence}`,
    type: 'TRANSFER',
    direction: 'DEBIT',
    amount: '100.00',
    currency: 'INR',
    balanceAfter: '900.00',
    counterpartyLast4: '3308',
    reference: null,
    postedAt: '2026-11-24T10:00:00Z',
    ...overrides,
  };
}

export function payment(overrides: Partial<Payment> = {}): Payment {
  return {
    paymentId: 'pay-x',
    type: 'TRANSFER',
    status: 'COMPLETED',
    fromAccountId: 'a-1',
    toAccountNumber: '502148363308',
    toAccountId: null,
    amount: '2500.00',
    currency: 'INR',
    reference: null,
    failureReason: null,
    createdAt: '2026-11-24T14:02:00Z',
    updatedAt: '2026-11-24T14:02:00Z',
    ...overrides,
  };
}
