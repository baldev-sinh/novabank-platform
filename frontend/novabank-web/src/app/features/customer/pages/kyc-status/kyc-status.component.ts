import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { stepFor } from '../../../../core/guards/onboarding.guard';
import { AlertComponent, BadgeComponent, ButtonComponent, CardComponent } from '../../../../shared/ui';
import { AuthenticationService } from '../../../auth/services/authentication.service';
import { CustomerService } from '../../customer.service';

/** While compliance reviews the identity check (PENDING_KYC). */
@Component({
  selector: 'nb-kyc-status',
  imports: [AlertComponent, BadgeComponent, ButtonComponent, CardComponent],
  template: `
    <nb-card padding="lg" class="panel">
      <nb-badge tone="attention">Identity check in progress</nb-badge>
      <h1 class="title">We're checking your identity</h1>
      <p class="lead">
        Thanks, {{ firstName() }}. Our compliance team reviews every new customer before money can
        move. You don't need to do anything else.
      </p>

      <h2 class="subtitle">What happens next</h2>
      <ol class="steps">
        <li>We check the details you gave us.</li>
        <li>Once they're approved, you can open current and savings accounts.</li>
        <li>Then you can receive money and make transfers.</li>
      </ol>

      @if (stillPending()) {
        <nb-alert tone="info">Not approved yet. Please check again later.</nb-alert>
      }
      @if (failed()) {
        <nb-alert tone="danger" title="We couldn't check right now.">
          Please try again in a moment.
        </nb-alert>
      }

      <div>
        <button nbButton="neutral" type="button" [busy]="busy()" (click)="checkAgain()">
          Check again
        </button>
      </div>
    </nb-card>
  `,
  styles: `
    .panel {
      max-width: 40rem;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: var(--nb-space-5);
    }
    .title {
      font-size: var(--nb-font-size-2xl);
      font-weight: var(--nb-font-weight-bold);
      letter-spacing: -0.01em;
    }
    .lead {
      margin-top: calc(-1 * var(--nb-space-3));
      color: var(--nb-color-slate);
      font-size: var(--nb-font-size-md);
    }
    .subtitle {
      font-size: var(--nb-font-size-lg);
    }
    .steps {
      margin: calc(-1 * var(--nb-space-2)) 0 0;
      padding-left: var(--nb-space-6);
      display: flex;
      flex-direction: column;
      gap: var(--nb-space-2);
      font-size: var(--nb-font-size-md);
    }
    nb-alert {
      align-self: stretch;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KycStatusComponent {
  private readonly customers = inject(CustomerService);
  private readonly router = inject(Router);
  private readonly user = inject(AuthenticationService).currentUser;

  protected readonly firstName = () => this.customers.profile()?.firstName ?? '';
  protected readonly busy = signal(false);
  protected readonly stillPending = signal(false);
  protected readonly failed = signal(false);

  protected checkAgain(): void {
    const user = this.user();
    if (!user || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.stillPending.set(false);
    this.failed.set(false);
    this.customers.load(user.id).subscribe({
      next: (profile) => {
        this.busy.set(false);
        const step = stepFor(profile);
        if (step === 'status') {
          this.stillPending.set(true);
        } else {
          void this.router.navigateByUrl(step === 'banking' ? '/' : '/onboarding/profile');
        }
      },
      error: () => {
        this.busy.set(false);
        this.failed.set(true);
      },
    });
  }
}
