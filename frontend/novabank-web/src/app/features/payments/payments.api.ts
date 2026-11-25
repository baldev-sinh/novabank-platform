import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Tone } from '../../shared/ui';

export type PaymentStatus =
  'INITIATED' | 'FUNDS_HELD' | 'LEDGER_POSTED' | 'COMPENSATING' | 'COMPLETED' | 'FAILED';

export interface Payment {
  paymentId: string;
  type: 'TRANSFER' | 'DEPOSIT';
  status: PaymentStatus;
  fromAccountId: string | null;
  toAccountNumber: string | null;
  toAccountId: string | null;
  amount: string;
  currency: string;
  reference: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentListPage {
  payments: Payment[];
  nextUrl: string | null;
}

/** The rel="next" target of a Link header, if it points into our own API. */
export function nextLink(header: string | null): string | null {
  const target = /<([^>]+)>\s*;\s*rel="next"/.exec(header ?? '')?.[1] ?? null;
  // Only a path on this origin: a Link header can't send the client (and its token) elsewhere.
  return target && target.startsWith(environment.api.baseUrl + '/') ? target : null;
}

export interface TransferRequest {
  fromAccountId: string;
  /** 12 digits, no spaces. */
  toAccountNumber: string;
  /** Decimal string in the account's currency. */
  amount: string;
  currency: string;
  reference?: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentsApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.api.baseUrl}/payments`;

  /** The customer's own payments, newest first (the newest 50). */
  list(): Observable<Payment[]> {
    return this.http.get<Payment[]>(this.url);
  }

  /**
   * One page of payments. The body is the plain array; the next (older) page's URL comes in the
   * `Link: <…>; rel="next"` header (RFC 8288), absent on the last page. Pass that URL back as
   * `nextUrl` to fetch it.
   */
  page(nextUrl: string | null = null, limit = 20): Observable<PaymentListPage> {
    return this.http
      .get<Payment[]>(nextUrl ?? this.url, {
        observe: 'response',
        params: nextUrl ? undefined : { limit: String(limit) },
      })
      .pipe(
        map((response) => ({
          payments: response.body ?? [],
          nextUrl: nextLink(response.headers.get('Link')),
        })),
      );
  }

  get(paymentId: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.url}/${encodeURIComponent(paymentId)}`);
  }

  /**
   * Starts a transfer; the server answers 202 at once and the saga finishes in the background, so
   * poll `get` for the outcome. The same `idempotencyKey` returns the same payment and never moves
   * money twice (ADR-003): reuse it for every retry of one confirmed transfer.
   */
  transfer(request: TransferRequest, idempotencyKey: string): Observable<Payment> {
    return this.http.post<Payment>(this.url, request, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }
}

/** Everything before COMPLETED / FAILED is still in progress for the customer. */
export function isFinished(payment: Payment): boolean {
  return payment.status === 'COMPLETED' || payment.status === 'FAILED';
}

export function statusLabel(payment: Payment): { text: string; tone: Tone } {
  if (payment.status === 'COMPLETED') {
    return { text: 'Completed', tone: 'success' };
  }
  if (payment.status === 'FAILED') {
    return { text: 'Not completed', tone: 'danger' };
  }
  return { text: 'Processing', tone: 'info' };
}

/**
 * Why a payment didn't go through, in words a customer can act on. Reasons come from the account
 * service (hold refused) and the ledger (posting refused); a reason added later reads generically.
 */
export function failureText(reason: string | null): string {
  switch (reason) {
    case 'INSUFFICIENT_FUNDS':
      return "There wasn't enough money available. Nothing was taken from your account.";
    case 'BENEFICIARY_NOT_FOUND':
    case 'UNKNOWN_ACCOUNT':
      return "We couldn't find an account with that number. Nothing was taken from your account.";
    case 'BENEFICIARY_NOT_ACTIVE':
      return "The account you're paying can't receive money right now. Nothing was taken.";
    case 'ACCOUNT_NOT_ACTIVE':
      return "Your account can't send money right now. Nothing was taken.";
    case 'SAME_ACCOUNT':
      return "You can't pay an account from itself. Nothing was taken.";
    case 'CURRENCY_MISMATCH':
      return 'The two accounts are in different currencies. Nothing was taken.';
    default:
      return "The payment couldn't be completed. Nothing was taken from your account.";
  }
}
