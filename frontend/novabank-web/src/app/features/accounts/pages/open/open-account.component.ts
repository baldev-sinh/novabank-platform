import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { problemOf, ProblemType } from '../../../../core/http/problem';
import { AlertComponent, ButtonComponent, CardComponent } from '../../../../shared/ui';
import { AccountType, AccountsApi } from '../../accounts.api';

/** The account service's `accounts.supported-currencies`; it refuses others with a 400. */
const CURRENCIES = [
  { code: 'INR', name: 'Indian rupee' },
  { code: 'EUR', name: 'Euro' },
  { code: 'USD', name: 'US dollar' },
  { code: 'GBP', name: 'Pound sterling' },
];

@Component({
  selector: 'nb-open-account',
  imports: [ReactiveFormsModule, RouterLink, AlertComponent, ButtonComponent, CardComponent],
  templateUrl: './open-account.component.html',
  styleUrl: './open-account.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpenAccountComponent {
  private readonly accounts = inject(AccountsApi);
  private readonly router = inject(Router);

  protected readonly currencies = CURRENCIES;
  readonly form = inject(FormBuilder).nonNullable.group({
    type: ['CURRENT' as AccountType],
    currency: ['INR'],
  });

  /** One key per form: a double click or a retry after a timeout opens one account, not two. */
  private readonly idempotencyKey = crypto.randomUUID();

  protected readonly busy = signal(false);
  protected readonly failure = signal<{ title: string; text: string } | null>(null);

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const { type, currency } = this.form.getRawValue();
    this.accounts.open(type, currency, this.idempotencyKey).subscribe({
      next: (account) => void this.router.navigate(['/accounts', account.accountId]),
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        if (problem.status === 403) {
          // KYC approval reaches the account service as an event: a just-approved customer can
          // be a moment ahead of it.
          this.failure.set({
            title: "We're still setting up your banking.",
            text: 'Your identity check has only just been approved. Please try again in a minute.',
          });
        } else if (problem.status === 400) {
          this.failure.set({
            title: "We couldn't open this account.",
            text: problem.detail ?? 'Please check your choices and try again.',
          });
        } else {
          this.failure.set({
            title:
              problem.type === ProblemType.NETWORK
                ? "We can't reach NovaBank right now."
                : 'Something went wrong on our side.',
            text: 'Please try again. You won’t end up with two accounts.',
          });
        }
      },
    });
  }
}
