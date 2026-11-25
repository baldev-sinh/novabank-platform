import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AccountType = 'CURRENT' | 'SAVINGS';
/** PENDING until the ledger confirms the account (roadmap 6.4); only ACTIVE accounts move money. */
export type AccountStatus = 'PENDING' | 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  accountId: string;
  accountNumber: string;
  type: AccountType;
  currency: string;
  status: AccountStatus;
  openedAt: string;
}

/** Decimal strings (Decision L). available = ledgerBalance − held. */
export interface Balance {
  accountId: string;
  currency: string;
  ledgerBalance: string;
  held: string;
  available: string;
  ledgerSequence: number;
}

/** One line of an account's history (account service, 8.1). CREDIT = money in. */
export interface Transaction {
  sequence: number;
  paymentId: string;
  type: 'DEPOSIT' | 'TRANSFER';
  direction: 'CREDIT' | 'DEBIT';
  amount: string;
  currency: string;
  balanceAfter: string;
  counterpartyLast4: string | null;
  reference: string | null;
  postedAt: string;
}

export interface TransactionPage {
  transactions: Transaction[];
  /** Pass as `before` for the next (older) page; null on the last page. */
  nextBefore: number | null;
}

@Injectable({ providedIn: 'root' })
export class AccountsApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.api.baseUrl}/accounts`;

  list(): Observable<Account[]> {
    return this.http.get<Account[]>(this.url);
  }

  get(accountId: string): Observable<Account> {
    return this.http.get<Account>(`${this.url}/${encodeURIComponent(accountId)}`);
  }

  balance(accountId: string): Observable<Balance> {
    return this.http.get<Balance>(`${this.url}/${encodeURIComponent(accountId)}/balance`);
  }

  /** `direction`: only money in (CREDIT) or out (DEBIT); keep it the same for every page. */
  transactions(
    accountId: string,
    before: number | null,
    limit = 25,
    direction: 'CREDIT' | 'DEBIT' | null = null,
  ): Observable<TransactionPage> {
    const params: Record<string, string> = { limit: String(limit) };
    if (before !== null) {
      params['before'] = String(before);
    }
    if (direction) {
      params['direction'] = direction;
    }
    return this.http.get<TransactionPage>(
      `${this.url}/${encodeURIComponent(accountId)}/transactions`,
      { params },
    );
  }

  /**
   * `idempotencyKey` is made once per form and reused on every retry of it, so a resent request
   * (double submit, retry after a timeout) returns the same account instead of opening a second.
   */
  open(type: AccountType, currency: string, idempotencyKey: string): Observable<Account> {
    return this.http.post<Account>(
      this.url,
      { type, currency },
      { headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }) },
    );
  }
}

export const ACCOUNT_TYPE_NAMES: Record<AccountType, string> = {
  CURRENT: 'Current account',
  SAVINGS: 'Savings account',
};
