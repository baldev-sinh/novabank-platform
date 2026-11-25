import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Problem, problemOf, ProblemType } from '../../../../core/http/problem';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FieldComponent,
  InputDirective,
} from '../../../../shared/ui';
import { AuthenticationService } from '../../services/authentication.service';

/** What the page tells the customer after a failed sign-in. */
type Failure =
  | { kind: 'credentials' }
  | { kind: 'unverified'; email: string; resent: boolean }
  | { kind: 'rate-limited'; minutes: number }
  | { kind: 'offline' }
  | { kind: 'unexpected'; reference?: string };

@Component({
  selector: 'nb-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AlertComponent,
    ButtonComponent,
    CardComponent,
    FieldComponent,
    InputDirective,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authenticationService = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly resending = signal(false);
  protected readonly passwordShown = signal(false);
  protected readonly failure = signal<Failure | null>(null);
  protected readonly unverified = computed(() => narrow(this.failure(), 'unverified'));
  protected readonly rateLimited = computed(() => narrow(this.failure(), 'rate-limited'));
  protected readonly unexpected = computed(() => narrow(this.failure(), 'unexpected'));
  /** Arriving from the email link (`/login?verified=1`). */
  protected readonly justVerified = this.route.snapshot.queryParamMap.get('verified') === '1';

  // Plain methods, not computed(): form state isn't a signal. Errors show only after a submit
  // attempt, then follow the field as it is corrected.
  protected emailError(): string | null {
    if (!this.submitted()) {
      return null;
    }
    const email = this.form.controls.email;
    if (email.hasError('required')) {
      return 'Enter your email address.';
    }
    return email.hasError('email') ? 'Enter an email address like name@example.com.' : null;
  }

  protected passwordError(): string | null {
    return this.submitted() && this.form.controls.password.hasError('required')
      ? 'Enter your password.'
      : null;
  }

  protected togglePassword(): void {
    this.passwordShown.update((shown) => !shown);
  }

  protected submit(): void {
    this.submitted.set(true);
    this.failure.set(null);
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    const credentials = this.form.getRawValue();
    this.authenticationService.signIn(credentials).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigateByUrl(this.safeReturnUrl());
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.failure.set(this.describe(problemOf(error), credentials.email));
      },
    });
  }

  protected resend(email: string): void {
    if (this.resending()) {
      return;
    }
    this.resending.set(true);
    this.authenticationService.resendVerification(email).subscribe({
      // The server answers the same whether or not the address exists; so does the page.
      next: () => {
        this.resending.set(false);
        this.failure.set({ kind: 'unverified', email, resent: true });
      },
      error: (error: unknown) => {
        this.resending.set(false);
        this.failure.set(this.describe(problemOf(error), email));
      },
    });
  }

  private describe(problem: Problem, email: string): Failure {
    if (problem.type === ProblemType.EMAIL_NOT_VERIFIED) {
      return { kind: 'unverified', email, resent: false };
    }
    if (problem.status === 401) {
      return { kind: 'credentials' };
    }
    if (problem.status === 429) {
      return {
        kind: 'rate-limited',
        minutes: Math.max(1, Math.ceil((problem.retryAfterSeconds ?? 60) / 60)),
      };
    }
    if (problem.type === ProblemType.NETWORK) {
      return { kind: 'offline' };
    }
    return { kind: 'unexpected', reference: problem.traceId };
  }

  /** Only paths inside the app: a crafted `returnUrl=https://…` can't send anyone elsewhere. */
  private safeReturnUrl(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/';
  }
}

function narrow<K extends Failure['kind']>(
  failure: Failure | null,
  kind: K,
): Extract<Failure, { kind: K }> | null {
  return failure?.kind === kind ? (failure as Extract<Failure, { kind: K }>) : null;
}
