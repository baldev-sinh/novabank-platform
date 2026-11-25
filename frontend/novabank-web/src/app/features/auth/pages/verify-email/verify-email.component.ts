import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { problemOf, ProblemType } from '../../../../core/http/problem';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FieldComponent,
  InputDirective,
} from '../../../../shared/ui';
import { AuthenticationService } from '../../services/authentication.service';

type State = 'verifying' | 'failed' | 'offline' | 'resent';

/**
 * The emailed link lands here as `/verify-email#token=…`. The token is in the fragment so it never
 * reaches a server or proxy log; it is read once and removed from the address bar and history.
 */
@Component({
  selector: 'nb-verify-email',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AlertComponent,
    ButtonComponent,
    CardComponent,
    FieldComponent,
    InputDirective,
  ],
  template: `
    <nb-card padding="lg" class="panel">
      @switch (state()) {
        @case ('verifying') {
          <h1 class="title">Verifying your email address</h1>
          <p class="lead" role="status">One moment…</p>
        }
        @case ('offline') {
          <h1 class="title">We can't reach NovaBank right now</h1>
          <p class="lead">Check your connection, then open the link from your email again.</p>
        }
        @default {
          <h1 class="title">This link doesn't work any more</h1>
          <p class="lead">
            Links work once and expire after a while. Enter your email address and we'll send a new
            one.
          </p>
          @if (state() === 'resent') {
            <nb-alert tone="success" title="Check your email.">
              If the address has an account with us, a new link is on its way.
            </nb-alert>
          }
          <form class="form" [formGroup]="form" (ngSubmit)="resend()" novalidate>
            <nb-field label="Email address" [error]="emailError()">
              <input nbInput type="email" autocomplete="email" formControlName="email" />
            </nb-field>
            <div class="actions">
              <button nbButton type="submit" [busy]="busy()">Send a new link</button>
              <a nbButton="neutral" routerLink="/login">Go to sign in</a>
            </div>
          </form>
        }
      }
    </nb-card>
  `,
  styleUrl: '../auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailComponent implements OnInit {
  private readonly authenticationService = inject(AuthenticationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected readonly state = signal<State>('verifying');
  protected readonly busy = signal(false);
  protected readonly submitted = signal(false);

  ngOnInit(): void {
    const token = new URLSearchParams(this.route.snapshot.fragment ?? '').get('token');
    // Out of the address bar and history before anything else happens.
    void this.router.navigate([], { replaceUrl: true, fragment: undefined });
    if (!token) {
      this.state.set('failed');
      return;
    }
    this.authenticationService.verifyEmail(token).subscribe({
      next: () => void this.router.navigate(['/login'], { queryParams: { verified: 1 } }),
      error: (error: unknown) =>
        this.state.set(problemOf(error).type === ProblemType.NETWORK ? 'offline' : 'failed'),
    });
  }

  protected emailError(): string | null {
    const email = this.form.controls.email;
    if (!this.submitted() || email.valid) {
      return null;
    }
    return email.hasError('required')
      ? 'Enter your email address.'
      : 'Enter an email address like name@example.com.';
  }

  protected resend(): void {
    this.submitted.set(true);
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.authenticationService.resendVerification(this.form.getRawValue().email).subscribe({
      next: () => {
        this.busy.set(false);
        this.state.set('resent');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.state.set(problemOf(error).type === ProblemType.NETWORK ? 'offline' : 'failed');
      },
    });
  }
}
