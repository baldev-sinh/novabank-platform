import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { fieldErrors, problemOf, ProblemType } from '../../../../core/http/problem';
import { stepFor } from '../../../../core/guards/onboarding.guard';
import {
  AlertComponent,
  ButtonComponent,
  CardComponent,
  FieldComponent,
  InputDirective,
} from '@novabank/ui';
import { AuthenticationService } from '../../../auth/services/authentication.service';
import { CustomerService } from '../../customer.service';

/** The customer service's PhoneNumber rule: E.164 once spaces, dashes, dots and brackets go. */
const SEPARATORS = /[\s\-().]/g;
const E164 = /^\+[1-9]\d{1,14}$/;
const NAME_MAX = 100;

/**
 * Onboarding, step 3 of 3: the details the identity check needs. Creates the customer profile
 * (PENDING_KYC); compliance then approves it.
 */
@Component({
  selector: 'nb-profile',
  imports: [
    ReactiveFormsModule,
    AlertComponent,
    ButtonComponent,
    CardComponent,
    FieldComponent,
    InputDirective,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent {
  private readonly customers = inject(CustomerService);
  private readonly router = inject(Router);
  private readonly user = inject(AuthenticationService).currentUser;

  readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(NAME_MAX)]],
    lastName: ['', [Validators.required, Validators.maxLength(NAME_MAX)]],
    day: [''],
    month: [''],
    year: [''],
    phone: ['', Validators.required],
  });

  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly serverErrors = signal<Record<string, string>>({});
  protected readonly failure = signal<string | null>(null);

  protected firstNameError(): string | null {
    return this.nameError('firstName', 'first name') ?? this.serverErrors()['firstName'] ?? null;
  }

  protected lastNameError(): string | null {
    return this.nameError('lastName', 'last name') ?? this.serverErrors()['lastName'] ?? null;
  }

  protected dateError(): string | null {
    if (this.submitted()) {
      const { error } = this.dateOfBirth();
      if (error) {
        return error;
      }
    }
    return this.serverErrors()['dateOfBirth'] ?? null;
  }

  protected phoneError(): string | null {
    if (this.submitted()) {
      const phone = this.form.controls.phone.value.trim();
      if (!phone) {
        return 'Enter your mobile number.';
      }
      if (!E164.test(phone.replace(SEPARATORS, ''))) {
        return 'Enter the number in international format, starting with + and the country code, for example +91 98765 43210.';
      }
    }
    return this.serverErrors()['phone'] ?? null;
  }

  protected clearServerError(field: string): void {
    if (this.serverErrors()[field]) {
      this.serverErrors.update(({ [field]: _removed, ...rest }) => rest);
    }
  }

  protected submit(): void {
    this.submitted.set(true);
    this.failure.set(null);
    const user = this.user();
    const date = this.dateOfBirth();
    if (!user || this.form.invalid || date.error || this.phoneError() || this.busy()) {
      return;
    }
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.customers
      .create(user.id, {
        firstName: value.firstName.trim(),
        lastName: value.lastName.trim(),
        dateOfBirth: date.iso!,
        phone: value.phone.replace(SEPARATORS, ''),
      })
      .subscribe({
        next: (profile) => {
          this.busy.set(false);
          void this.router.navigateByUrl(
            stepFor(profile) === 'banking' ? '/' : '/onboarding/status',
          );
        },
        error: (error: unknown) => this.failed(user.id, error),
      });
  }

  private failed(userId: string, error: unknown): void {
    this.busy.set(false);
    const problem = problemOf(error);
    if (problem.status === 409) {
      // Created already (e.g. in another tab): carry on from wherever onboarding has got to.
      this.customers
        .load(userId)
        .subscribe((profile) =>
          this.router.navigateByUrl(stepFor(profile) === 'banking' ? '/' : '/onboarding/status'),
        );
    } else if (problem.status === 400 && problem.errors?.length) {
      this.serverErrors.set(fieldErrors(problem));
    } else if (problem.status === 400 && problem.detail) {
      this.failure.set(problem.detail);
    } else {
      this.failure.set(
        problem.type === ProblemType.NETWORK
          ? "We can't reach NovaBank right now. Check your connection and try again."
          : 'Something went wrong on our side. Please try again in a moment.',
      );
    }
  }

  private nameError(control: 'firstName' | 'lastName', label: string): string | null {
    if (!this.submitted()) {
      return null;
    }
    const field = this.form.controls[control];
    if (!field.value.trim()) {
      return `Enter your ${label}.`;
    }
    return field.hasError('maxlength') ? `Use at most ${NAME_MAX} characters.` : null;
  }

  /** The three boxes as one date: an ISO string, or what is wrong with it. */
  private dateOfBirth(): { iso?: string; error?: string } {
    const { day, month, year } = this.form.getRawValue();
    const parts = [day, month, year].map((part) => part.trim());
    if (parts.every((part) => !part)) {
      return { error: 'Enter your date of birth.' };
    }
    if (parts.some((part) => !/^\d+$/.test(part))) {
      return { error: 'Date of birth must include a day, month and year, in numbers.' };
    }
    const [d, m, y] = parts.map(Number);
    if (parts[2].length !== 4) {
      return { error: 'Year must have 4 numbers, for example 1990.' };
    }
    const date = new Date(Date.UTC(y, m - 1, d));
    if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) {
      return { error: 'Date of birth must be a real date.' };
    }
    if (y < 1900) {
      return { error: 'Year must be 1900 or later.' };
    }
    const iso = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    if (iso >= new Date().toISOString().slice(0, 10)) {
      return { error: 'Date of birth must be in the past.' };
    }
    return { iso };
  }
}
