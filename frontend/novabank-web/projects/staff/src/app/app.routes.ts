import { Routes } from '@angular/router';
import { SignedOutLayoutComponent } from './layout/signed-out-layout.component';

/**
 * The staff back-office (Phase 10, Decision AR). Signed-out pages only for now: staff sign-in
 * with an authenticator code (10.2) adds the signed-in shell and its guarded tools.
 */
export const routes: Routes = [
  {
    path: '',
    component: SignedOutLayoutComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'sign-in' },
      {
        path: 'sign-in',
        title: 'Sign in — NovaBank back-office',
        loadComponent: () =>
          import('./pages/sign-in/sign-in.component').then((m) => m.SignInComponent),
      },
      {
        path: '**',
        title: 'Page not found — NovaBank back-office',
        loadComponent: () =>
          import('./pages/not-found/not-found.component').then((m) => m.NotFoundComponent),
      },
    ],
  },
];
