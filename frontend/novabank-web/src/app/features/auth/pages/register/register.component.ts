import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { fieldErrors, problemOf, ProblemType } from '../../../../core/http/problem';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FieldComponent,
  InputDirective,
} from '../../../../shared/ui';
import { AuthenticationService } from '../../services/authentication.service';

/** The auth service's rule (RegisterUserRequest): long passphrases over complexity rules. */
const PASSWORD_MIN = 15;
const PASSWORD_MAX = 64;

@Component({
  selector: 'nb-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AlertComponent,
    ButtonComponent,
    CardComponent,
    FieldComponent,
    InputDirective,
  ],
  templateUrl: './register.component.html',
  styleUrl: '../auth-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authenticationService = inject(AuthenticationService);
  private readonly router = inject(Router);

  protected readonly passwordHint =
    `${PASSWORD_MIN} to ${PASSWORD_MAX} characters. ` +
    'A few unrelated words make a strong password that is easy to remember.';

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: [
      '',
      [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)],
    ],
  });

  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly passwordShown = signal(false);
  /** Messages from the server for one field (400 validation, 409 email taken). */
  protected readonly serverErrors = signal<Record<string, string>>({});
  protected readonly failed = signal<'offline' | 'unexpected' | null>(null);

  protected emailError(): string | null {
    const email = this.form.controls.email;
    if (this.submitted()) {
      if (email.hasError('required')) {
        return 'Enter your email address.';
      }
      if (email.hasError('email') || email.hasError('maxlength')) {
        return 'Enter an email address like name@example.com.';
      }
    }
    return this.serverErrors()['email'] ?? null;
  }

  protected passwordError(): string | null {
    const password = this.form.controls.password;
    if (this.submitted()) {
      if (password.hasError('required')) {
        return 'Choose a password.';
      }
      if (password.hasError('minlength')) {
        return `Use at least ${PASSWORD_MIN} characters. You have ${password.value.length}.`;
      }
      if (password.hasError('maxlength')) {
        return `Use at most ${PASSWORD_MAX} characters.`;
      }
    }
    return this.serverErrors()['password'] ?? null;
  }

  protected togglePassword(): void {
    this.passwordShown.update((shown) => !shown);
  }

  protected clearServerError(field: string): void {
    if (this.serverErrors()[field]) {
      this.serverErrors.update(({ [field]: _removed, ...rest }) => rest);
    }
  }

  protected submit(): void {
    this.submitted.set(true);
    this.failed.set(null);
    if (this.form.invalid || this.busy()) {
      return;
    }
    this.busy.set(true);
    const request = this.form.getRawValue();
    this.authenticationService.register(request).subscribe({
      next: () => {
        this.busy.set(false);
        void this.router.navigate(['/register/check-email'], { state: { email: request.email } });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = problemOf(error);
        if (problem.status === 409) {
          this.serverErrors.set({
            email: 'There is already an account with this email address. Sign in instead.',
          });
        } else if (problem.status === 400 && problem.errors?.length) {
          this.serverErrors.set(fieldErrors(problem));
        } else {
          this.failed.set(problem.type === ProblemType.NETWORK ? 'offline' : 'unexpected');
        }
      },
    });
  }
}
