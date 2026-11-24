import { Clipboard } from '@angular/cdk/clipboard';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { problemOf } from '../../../../core/http/problem';
import {
  AccountNumberPipe,
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  MoneyPipe,
} from '../../../../shared/ui';
import { isFinished, Payment, PaymentsApi } from '../../../payments/payments.api';
import { Account, ACCOUNT_TYPE_NAMES, AccountsApi, Balance, Transaction } from '../../accounts.api';
import { BalanceVisibility } from '../../balance-visibility.service';

const PAGE_SIZE = 25;

/** A row of the history table: a posted ledger line, or a transfer still in progress. */
interface Row {
  key: string;
  date: string;
  description: string;
  reference: string | null;
  amount: string;
  currency: string;
  direction: 'CREDIT' | 'DEBIT';
  balanceAfter: string | null;
  pending: boolean;
}

@Component({
  selector: 'nb-account-detail',
  imports: [
    DatePipe,
    RouterLink,
    AccountNumberPipe,
    AlertComponent,
    BadgeComponent,
    ButtonComponent,
    CardComponent,
    MoneyPipe,
  ],
  templateUrl: './detail.component.html',
  styleUrl: './detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailComponent implements OnInit {
  /** From the route, `/accounts/:accountId` (withComponentInputBinding). */
  readonly accountId = input.required<string>();

  private readonly accountsApi = inject(AccountsApi);
  private readonly paymentsApi = inject(PaymentsApi);
  private readonly clipboard = inject(Clipboard);
  protected readonly visibility = inject(BalanceVisibility);

  protected readonly typeNames = ACCOUNT_TYPE_NAMES;
  protected readonly state = signal<'loading' | 'ready' | 'not-found' | 'failed'>('loading');
  protected readonly account = signal<Account | null>(null);
  protected readonly balance = signal<Balance | null>(null);
  private readonly posted = signal<Transaction[]>([]);
  private readonly inProgress = signal<Payment[]>([]);
  protected readonly nextBefore = signal<number | null>(null);
  protected readonly loadingMore = signal(false);
  protected readonly moreFailed = signal(false);
  protected readonly copied = signal(false);
  protected readonly errorReference = signal<string | undefined>(undefined);

  protected readonly rows = computed<Row[]>(() => [
    ...this.inProgress().map((payment) => ({
      key: 'p-' + payment.paymentId,
      date: payment.createdAt,
      description: `Transfer to account ending ${(payment.toAccountNumber ?? '').slice(-4)}`,
      reference: payment.reference,
      amount: payment.amount,
      currency: payment.currency,
      direction: 'DEBIT' as const,
      balanceAfter: null,
      pending: true,
    })),
    ...this.posted().map((line) => ({
      key: 't-' + line.sequence,
      date: line.postedAt,
      description: describe(line),
      reference: line.reference,
      amount: line.amount,
      currency: line.currency,
      direction: line.direction,
      balanceAfter: line.balanceAfter,
      pending: false,
    })),
  ]);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const id = this.accountId();
    this.state.set('loading');
    forkJoin({
      account: this.accountsApi.get(id),
      balance: this.accountsApi.balance(id).pipe(catchError(() => of(null))),
      page: this.accountsApi.transactions(id, null, PAGE_SIZE),
      // In-progress transfers are on the payments list until the ledger posts them.
      payments: this.paymentsApi.list().pipe(catchError(() => of([] as Payment[]))),
    }).subscribe({
      next: ({ account, balance, page, payments }) => {
        this.account.set(account);
        this.balance.set(balance);
        this.posted.set(page.transactions);
        this.nextBefore.set(page.nextBefore);
        this.inProgress.set(
          payments.filter((p) => p.fromAccountId === id && !isFinished(p)),
        );
        this.state.set('ready');
      },
      error: (error: unknown) => {
        const problem = problemOf(error);
        // Someone else's account answers 404 too, so the page can't tell them apart either.
        this.state.set(problem.status === 404 ? 'not-found' : 'failed');
        this.errorReference.set(problem.traceId);
      },
    });
  }

  protected loadOlder(): void {
    const before = this.nextBefore();
    if (before === null || this.loadingMore()) {
      return;
    }
    this.loadingMore.set(true);
    this.moreFailed.set(false);
    this.accountsApi.transactions(this.accountId(), before, PAGE_SIZE).subscribe({
      next: (page) => {
        this.posted.update((lines) => [...lines, ...page.transactions]);
        this.nextBefore.set(page.nextBefore);
        this.loadingMore.set(false);
      },
      error: () => {
        this.loadingMore.set(false);
        this.moreFailed.set(true);
      },
    });
  }

  protected copyNumber(): void {
    const account = this.account();
    if (account && this.clipboard.copy(account.accountNumber)) {
      this.copied.set(true);
    }
  }
}

function describe(line: Transaction): string {
  if (line.type === 'DEPOSIT') {
    return 'Deposit';
  }
  const other = line.counterpartyLast4 ? ` account ending ${line.counterpartyLast4}` : ' another account';
  return line.direction === 'CREDIT' ? `Transfer from${other}` : `Transfer to${other}`;
}
