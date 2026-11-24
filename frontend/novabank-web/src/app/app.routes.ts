import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { PublicLayoutComponent } from './layout/public-layout/public-layout.component';

// Each page is its own lazy chunk; `title` sets document.title, which screen readers announce on
// navigation in a single-page app.
export const routes: Routes = [
  // Signed-in pages first. An empty-path parent matches even when none of its children does, so
  // with the public layout first, "/" would render that layout with an empty outlet. Here the
  // child '' (pathMatch: 'full') doesn't match "/login", so those URLs fall through to the public
  // routes without the auth guard ever running.
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Your accounts — NovaBank',
        loadComponent: () =>
          import('./features/dashboard/pages/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent,
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
