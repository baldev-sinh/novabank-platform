import { TokenStorageService } from './../../services/token-storage.service';
import { AuthenticationService } from './../../services/authentication.service';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

@Component({
  selector: 'nb-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authenticationService = inject(AuthenticationService);
  private readonly tokenStorageService = inject(TokenStorageService);

  readonly loginForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  onSubmit(): void {
    if (this.loginForm.invalid) {
      return;
    }

    this.authenticationService.login(this.loginForm.getRawValue()).subscribe({
      next: (response) => {
        this.tokenStorageService.saveAccessToken(response.accessToken);

        console.log('Login successful');
      },
      error: (error) => {
        console.error('Login failed', error);
      },
    });
  }
}
