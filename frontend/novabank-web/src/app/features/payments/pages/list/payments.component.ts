import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../../../core/http/problem';
import {
  AccountNumberPipe,
  AlertComponent,
  BadgeComponent,
  ButtonComponent,
  CardComponent,
  MoneyPipe,
} from '../../../../shared/ui';
import { BalanceVisibility } from '../../../accounts/balance-visibility.service';
import { failureText, Payment, PaymentsApi, statusLabel } from '../../payments.api';

/** Every payment the customer has made, newest first, a page at a time (9.4). */
@Component({
  selector: 'nb-payments',
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
  templateUrl: './payments.component.html',
  styleUrl: './payments.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentsComponent implements OnInit {
  private readonly paymentsApi = inject(PaymentsApi);
  protected readonly visibility = inject(BalanceVisibility);

  protected readonly state = signal<'loading' | 'ready' | 'failed'>('loading');
  protected readonly items = signal<
    { payment: Payment; status: ReturnType<typeof statusLabel>; failure: string | null }[]
  >([]);
  protected readonly nextUrl = signal<string | null>(null);
  protected readonly loadingMore = signal(false);
  protected readonly moreFailed = signal(false);
  protected readonly errorReference = signal<string | undefined>(undefined);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.paymentsApi.page().subscribe({
      next: (page) => {
        this.items.set(page.payments.map(describe));
        this.nextUrl.set(page.nextUrl);
        this.state.set('ready');
      },
      error: (error: unknown) => {
        this.errorReference.set(problemOf(error).traceId);
        this.state.set('failed');
      },
    });
  }

  protected loadOlder(): void {
    const next = this.nextUrl();
    if (!next || this.loadingMore()) {
      return;
    }
    this.loadingMore.set(true);
    this.moreFailed.set(false);
    this.paymentsApi.page(next).subscribe({
      next: (page) => {
        this.items.update((items) => [...items, ...page.payments.map(describe)]);
        this.nextUrl.set(page.nextUrl);
        this.loadingMore.set(false);
      },
      error: () => {
        this.loadingMore.set(false);
        this.moreFailed.set(true);
      },
    });
  }
}

function describe(payment: Payment) {
  return {
    payment,
    status: statusLabel(payment),
    failure: payment.status === 'FAILED' ? failureText(payment.failureReason) : null,
  };
}
