import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AlertComponent, ButtonComponent, CardComponent } from '@novabank/ui';
import { AuthenticationService } from '../../services/authentication.service';

/** After registering: where the link went, and how to get another one. */
@Component({
  selector: 'nb-check-email',
  imports: [RouterLink, AlertComponent, ButtonComponent, CardComponent],
  template: `
    <nb-card padding="lg" class="panel">
      <h1 class="title">Check your email</h1>
      @if (email) {
        <p class="lead">
          We've sent a link to <strong>{{ email }}</strong>. Open it on this device to verify your
          address. It works once.
        </p>
      } @else {
        <p class="lead">We've sent you a link. Open it to verify your email address.</p>
      }

      @if (resent()) {
        <nb-alert tone="success" title="Sent again.">
          Use the newest link: it replaces the earlier ones.
        </nb-alert>
      }
      @if (resendFailed()) {
        <nb-alert tone="danger" title="We couldn't send it."
          >Please try again in a moment.</nb-alert
        >
      }

      <p>Nothing there after a few minutes? Check your spam folder, or ask for a new link.</p>
      <div class="actions">
        @if (email) {
          <button nbButton="neutral" type="button" [busy]="busy()" (click)="resend()">
            Send the link again
          </button>
        }
        <a nbButton="secondary" routerLink="/login">Go to sign in</a>
      </div>
    </nb-card>
  `,
  styleUrl: '../auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckEmailComponent {
  private readonly authenticationService = inject(AuthenticationService);

  /** From the register page's navigation state; gone after a reload, so the page copes without. */
  protected readonly email: string | null =
    (inject(Router).currentNavigation()?.extras.state?.['email'] as string | undefined) ??
    (history.state?.email as string | undefined) ??
    null;

  protected readonly busy = signal(false);
  protected readonly resent = signal(false);
  protected readonly resendFailed = signal(false);

  protected resend(): void {
    if (!this.email || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.resent.set(false);
    this.resendFailed.set(false);
    this.authenticationService.resendVerification(this.email).subscribe({
      next: () => {
        this.busy.set(false);
        this.resent.set(true);
      },
      error: () => {
        this.busy.set(false);
        this.resendFailed.set(true);
      },
    });
  }
}
