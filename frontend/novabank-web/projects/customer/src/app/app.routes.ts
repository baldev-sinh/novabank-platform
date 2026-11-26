import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { onboardingGuard } from './core/guards/onboarding.guard';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { PublicLayoutComponent } from './layout/public-layout/public-layout.component';

// Each page is its own lazy chunk; `title` sets document.title, which screen readers announce on
// navigation in a single-page app.
export const routes: Routes = [
  // Signed-in pages first. An empty-path parent matches even when none of its children does, so
  // with the public layout first, "/" would render that layout with an empty outlet. Here no
  // child matches "/login" and the like, so those URLs fall through to the public routes without
  // the auth guard ever running.
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [onboardingGuard('banking')],
        title: 'Your accounts — NovaBank',
        loadComponent: () =>
          import('./features/accounts/pages/overview/overview.component').then(
            (m) => m.OverviewComponent,
          ),
      },
      {
        path: 'payments',
        canActivate: [onboardingGuard('banking')],
        title: 'Payments — NovaBank',
        loadComponent: () =>
          import('./features/payments/pages/list/payments.component').then(
            (m) => m.PaymentsComponent,
          ),
      },
      {
        path: 'transfer',
        canActivate: [onboardingGuard('banking')],
        title: 'Transfer money — NovaBank',
        loadComponent: () =>
          import('./features/payments/pages/transfer/transfer.component').then(
            (m) => m.TransferComponent,
          ),
      },
      {
        // Before ':accountId', which would otherwise take "open" for an id.
        path: 'accounts/open',
        canActivate: [onboardingGuard('banking')],
        title: 'Open an account — NovaBank',
        loadComponent: () =>
          import('./features/accounts/pages/open/open-account.component').then(
            (m) => m.OpenAccountComponent,
          ),
      },
      {
        path: 'accounts/:accountId',
        canActivate: [onboardingGuard('banking')],
        title: 'Account — NovaBank',
        loadComponent: () =>
          import('./features/accounts/pages/detail/detail.component').then(
            (m) => m.DetailComponent,
          ),
      },
      {
        path: 'onboarding/profile',
        canActivate: [onboardingGuard('profile')],
        title: 'Tell us about yourself — NovaBank',
        loadComponent: () =>
          import('./features/customer/pages/profile/profile.component').then(
            (m) => m.ProfileComponent,
          ),
      },
      {
        path: 'onboarding/status',
        canActivate: [onboardingGuard('status')],
        title: 'Identity check — NovaBank',
        loadComponent: () =>
          import('./features/customer/pages/kyc-status/kyc-status.component').then(
            (m) => m.KycStatusComponent,
          ),
      },
    ],
  },
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      {
        path: 'login',
        canActivate: [guestGuard],
        title: 'Sign in — NovaBank',
        loadComponent: () =>
          import('./features/auth/pages/login/login.component').then((m) => m.LoginComponent),
      },
      {
        path: 'register',
        canActivate: [guestGuard],
        title: 'Register — NovaBank',
        loadComponent: () =>
          import('./features/auth/pages/register/register.component').then(
            (m) => m.RegisterComponent,
          ),
      },
      {
        path: 'register/check-email',
        title: 'Check your email — NovaBank',
        loadComponent: () =>
          import('./features/auth/pages/check-email/check-email.component').then(
            (m) => m.CheckEmailComponent,
          ),
      },
      {
        // Opened from the email, signed in or not.
        path: 'verify-email',
        title: 'Verify your email address — NovaBank',
        loadComponent: () =>
          import('./features/auth/pages/verify-email/verify-email.component').then(
            (m) => m.VerifyEmailComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
