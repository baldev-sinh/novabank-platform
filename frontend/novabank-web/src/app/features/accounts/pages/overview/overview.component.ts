import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, Observable, of, switchMap } from 'rxjs';
import { problemOf } from '../../../../core/http/problem';
import {
  AccountNumberPipe,
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  MoneyPipe,
} from '../../../../shared/ui';
import { sumDecimals } from '../../../../shared/ui/decimal';
import { CustomerService } from '../../../customer/customer.service';
import {
  failureText,
  isFinished,
  Payment,
  PaymentsApi,
  statusLabel,
} from '../../../payments/payments.api';
import { Account, ACCOUNT_TYPE_NAMES, AccountsApi, Balance } from '../../accounts.api';
import { BalanceVisibility } from '../../balance-visibility.service';

/** An account with its balance; `balance` is null if that one call failed. */
interface AccountRow {
  account: Account;
  balance: Balance | null;
}

/** How often, and how many times, to re-check accounts the ledger hasn't confirmed yet. */
const PENDING_POLL_MS = 3000;
const PENDING_POLL_MAX = 20;
const RECENT_PAYMENTS = 5;

@Component({
  selector: 'nb-accounts-overview',
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
  templateUrl: './overview.component.html',
  styleUrl: './overview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewComponent implements OnInit {
  private readonly accountsApi = inject(AccountsApi);
  private readonly paymentsApi = inject(PaymentsApi);
  private readonly customers = inject(CustomerService);
  protected readonly visibility = inject(BalanceVisibility);

  protected readonly typeNames = ACCOUNT_TYPE_NAMES;
  protected readonly state = signal<'loading' | 'ready' | 'failed'>('loading');
  protected readonly rows = signal<AccountRow[]>([]);
  protected readonly payments = signal<Payment[] | null>(null);
  protected readonly errorReference = signal<string | undefined>(undefined);

  protected readonly greeting = computed(() => {
    const name = this.customers.profile()?.firstName;
    const hour = new Date().getHours();
    const part = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
    return name ? `${part}, ${name}` : part;
  });

  /** Available money across active accounts, per currency, added exactly. */
  protected readonly totals = computed(() => {
    const byCurrency = new Map<string, string[]>();
    for (const { account, balance } of this.rows()) {
      if (account.status === 'ACTIVE' && balance) {
        byCurrency.set(account.currency, [
          ...(byCurrency.get(account.currency) ?? []),
          balance.available,
        ]);
      }
    }
    return [...byCurrency].map(([currency, amounts]) => ({
      currency,
      amount: sumDecimals(amounts),
    }));
  });

  protected readonly activeCount = computed(
    () => this.rows().filter((row) => row.account.status === 'ACTIVE').length,
  );

  protected readonly recentPayments = computed(() =>
    (this.payments() ?? []).slice(0, RECENT_PAYMENTS).map((payment) => ({
      payment,
      status: statusLabel(payment),
      failure: payment.status === 'FAILED' ? failureText(payment.failureReason) : null,
      inProgress: !isFinished(payment),
    })),
  );

  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private polls = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.loadAccounts().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.state.set('ready');
        this.pollWhilePending(rows);
      },
      error: (error: unknown) => {
        this.errorReference.set(problemOf(error).traceId);
        this.state.set('failed');
      },
    });
    // Payments are a side panel: if they fail, the accounts still show.
    this.paymentsApi
      .list()
      .pipe(catchError(() => of(null)))
      .subscribe((payments) => this.payments.set(payments));
  }

  private loadAccounts(): Observable<AccountRow[]> {
    return this.accountsApi.list().pipe(
      switchMap((accounts) =>
        accounts.length === 0
          ? of([])
          : forkJoin(
              accounts.map((account) =>
                this.accountsApi.balance(account.accountId).pipe(
                  map((balance): AccountRow => ({ account, balance })),
                  catchError(() => of<AccountRow>({ account, balance: null })),
                ),
              ),
            ),
      ),
    );
  }

  /** A new account turns ACTIVE when the ledger confirms it, usually within seconds. */
  private pollWhilePending(rows: AccountRow[]): void {
    this.stopPolling();
    if (!rows.some((row) => row.account.status === 'PENDING') || this.polls >= PENDING_POLL_MAX) {
      return;
    }
    this.pollTimer = setTimeout(() => {
      this.polls++;
      this.loadAccounts().subscribe({
        next: (fresh) => {
          this.rows.set(fresh);
          this.pollWhilePending(fresh);
        },
        error: () => this.pollWhilePending(rows),
      });
    }, PENDING_POLL_MS);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }
}
