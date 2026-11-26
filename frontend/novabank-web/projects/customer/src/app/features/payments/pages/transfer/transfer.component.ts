import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { problemOf, ProblemType } from '../../../../core/http/problem';
import {
  AccountNumberPipe,
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FieldComponent,
  InputDirective,
  MoneyPipe,
  compareDecimals,
} from '@novabank/ui';
import { Account, ACCOUNT_TYPE_NAMES, AccountsApi, Balance } from '../../../accounts/accounts.api';
import { failureText, isFinished, Payment, PaymentsApi } from '../../payments.api';

/** The payment service's `payments.max-amount`; it refuses more with a 400 either way. */
const MAX_AMOUNT = '1000000';
const REFERENCE_MAX = 140;
const POLL_MS = 1000;
const POLL_MAX = 30;

type Step = 'details' | 'review' | 'sending' | 'done';
type Outcome = 'completed' | 'failed' | 'processing';

interface Source {
  account: Account;
  balance: Balance;
}

@Component({
  selector: 'nb-transfer',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AccountNumberPipe,
    AlertComponent,
    ButtonComponent,
    CardComponent,
    FieldComponent,
    InputDirective,
    MoneyPipe,
  ],
  templateUrl: './transfer.component.html',
  styleUrl: './transfer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferComponent implements OnInit {
  /** `?from=<accountId>` preselects the account (from an account's own page). */
  readonly from = input<string | undefined>();

  private readonly accountsApi = inject(AccountsApi);
  private readonly paymentsApi = inject(PaymentsApi);

  protected readonly typeNames = ACCOUNT_TYPE_NAMES;
  protected readonly referenceMax = REFERENCE_MAX;

  readonly form = inject(FormBuilder).nonNullable.group({
    fromAccountId: [''],
    to: [''],
    amount: [''],
    reference: [''],
  });

  protected readonly loading = signal<'loading' | 'ready' | 'failed'>('loading');
  protected readonly sources = signal<Source[]>([]);
  protected readonly step = signal<Step>('details');
  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly sendError = signal<string | null>(null);
  protected readonly outcome = signal<Outcome | null>(null);
  protected readonly payment = signal<Payment | null>(null);

  /** One key per confirmed set of details; retries of that confirmation reuse it. */
  private idempotencyKey: string | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly steps = computed(() => {
    const index = { details: 0, review: 1, sending: 2, done: 2 }[this.step()];
    return ['Details', 'Review', 'Sent'].map((label, i) => ({
      label: `${i + 1}. ${label}`,
      state: i < index ? 'done' : i === index ? 'current' : 'todo',
    }));
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  ngOnInit(): void {
    this.loadSources();
  }

  protected loadSources(): void {
    this.loading.set('loading');
    this.accountsApi
      .list()
      .pipe(
        map((accounts) => accounts.filter((account) => account.status === 'ACTIVE')),
        switchMap((accounts) =>
          accounts.length === 0
            ? of([])
            : forkJoin(
                accounts.map((account) =>
                  this.accountsApi.balance(account.accountId).pipe(
                    map((balance): Source | null => ({ account, balance })),
                    catchError(() => of(null)),
                  ),
                ),
              ),
        ),
      )
      .subscribe({
        next: (sources) => {
          const usable = sources.filter((source): source is Source => source !== null);
          this.sources.set(usable);
          const wanted = usable.find((s) => s.account.accountId === this.from());
          this.form.controls.fromAccountId.setValue((wanted ?? usable[0])?.account.accountId ?? '');
          this.loading.set('ready');
        },
        error: () => this.loading.set('failed'),
      });
  }

  protected selected(): Source | undefined {
    return this.sources().find(
      (s) => s.account.accountId === this.form.controls.fromAccountId.value,
    );
  }

  // ---- details ----------------------------------------------------------------------------

  protected toNumber(): string {
    return this.form.controls.to.value.replace(/\s/g, '');
  }

  protected toError(): string | null {
    if (!this.submitted()) {
      return null;
    }
    const to = this.toNumber();
    if (!/^\d{12}$/.test(to)) {
      return 'Enter the 12-digit account number.';
    }
    if (to === this.selected()?.account.accountNumber) {
      return "That's the account you're paying from. Choose a different one.";
    }
    return null;
  }

  protected amountValue(): string {
    return this.form.controls.amount.value.replace(/[,\s]/g, '');
  }

  protected amountError(): string | null {
    if (!this.submitted()) {
      return null;
    }
    const source = this.selected();
    const amount = this.amountValue();
    if (!amount) {
      return 'Enter an amount.';
    }
    if (!/^\d{1,15}(\.\d+)?$/.test(amount)) {
      return 'Enter an amount in numbers, for example 2500 or 2500.50.';
    }
    if (compareDecimals(amount, '0') <= 0) {
      return 'Enter an amount greater than zero.';
    }
    if (!source) {
      return null;
    }
    const decimals = minorUnits(source.account.currency);
    if ((amount.split('.')[1] ?? '').length > decimals) {
      return decimals === 0
        ? 'Enter a whole amount, without decimals.'
        : `Use up to ${decimals} decimal places.`;
    }
    if (compareDecimals(amount, MAX_AMOUNT) > 0) {
      return `The most you can send in one transfer is ${new MoneyPipe().transform(MAX_AMOUNT, source.account.currency)}.`;
    }
    if (compareDecimals(amount, source.balance.available) > 0) {
      return 'This is more than the money available in this account.';
    }
    return null;
  }

  protected referenceError(): string | null {
    return this.form.controls.reference.value.length > REFERENCE_MAX
      ? `Use at most ${REFERENCE_MAX} characters.`
      : null;
  }

  protected continue(): void {
    this.submitted.set(true);
    if (!this.selected() || this.toError() || this.amountError() || this.referenceError()) {
      return;
    }
    // New details, new key: this is a different payment from any earlier attempt.
    this.idempotencyKey = crypto.randomUUID();
    this.sendError.set(null);
    this.step.set('review');
  }

  protected changeDetails(): void {
    this.step.set('details');
  }

  // ---- send and follow ---------------------------------------------------------------------

  protected confirm(): void {
    const source = this.selected();
    if (!source || !this.idempotencyKey || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.sendError.set(null);
    const reference = this.form.controls.reference.value.trim();
    this.paymentsApi
      .transfer(
        {
          fromAccountId: source.account.accountId,
          toAccountNumber: this.toNumber(),
          amount: this.amountValue(),
          currency: source.account.currency,
          ...(reference ? { reference } : {}),
        },
        this.idempotencyKey,
      )
      .subscribe({
        next: (payment) => {
          this.busy.set(false);
          this.payment.set(payment);
          this.step.set('sending');
          this.follow(payment, 0);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = problemOf(error);
          // Same key on the next try: if the first request did arrive, the server answers with
          // that payment instead of sending the money again.
          this.sendError.set(
            problem.status === 400 && problem.detail
              ? problem.detail
              : problem.type === ProblemType.NETWORK
                ? "We can't reach NovaBank right now. Try again: you won't be charged twice."
                : "Something went wrong on our side. Try again: you won't be charged twice.",
          );
        },
      });
  }

  private follow(payment: Payment, polls: number): void {
    if (isFinished(payment)) {
      this.finish(payment.status === 'COMPLETED' ? 'completed' : 'failed', payment);
      return;
    }
    if (polls >= POLL_MAX) {
      this.finish('processing', payment);
      return;
    }
    this.pollTimer = setTimeout(() => {
      this.paymentsApi.get(payment.paymentId).subscribe({
        next: (latest) => this.follow(latest, polls + 1),
        // A failed check isn't a failed payment: keep following it.
        error: () => this.follow(payment, polls + 1),
      });
    }, POLL_MS);
  }

  private finish(outcome: Outcome, payment: Payment): void {
    this.payment.set(payment);
    this.outcome.set(outcome);
    this.step.set('done');
  }

  protected failureText(): string {
    return failureText(this.payment()?.failureReason ?? null);
  }

  protected another(): void {
    this.stopPolling();
    this.form.patchValue({ to: '', amount: '', reference: '' });
    this.submitted.set(false);
    this.payment.set(null);
    this.outcome.set(null);
    this.idempotencyKey = null;
    this.step.set('details');
    this.loadSources(); // balances have changed
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }
}

/** The currency's minor units (2 for INR, 0 for JPY), as the payment service checks them. */
function minorUnits(currency: string): number {
  return (
    new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
      .maximumFractionDigits ?? 2
  );
}
