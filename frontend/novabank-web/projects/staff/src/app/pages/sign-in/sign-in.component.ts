import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AlertComponent } from '@novabank/ui';

/**
 * Staff sign-in. Until two-step sign-in with an authenticator code is in place (10.2), the
 * back-office can't be signed in to: staff tools stay on the API, behind their roles.
 */
@Component({
  selector: 'nb-sign-in',
  imports: [AlertComponent],
  template: `
    <h1 class="title">Sign in to the back-office</h1>
    <nb-alert tone="info" title="Staff sign-in isn't available yet.">
      Two-step sign-in with an authenticator app is being set up. Until then, staff tools are
      available through the API only.
    </nb-alert>
  `,
  styles: `
    .title {
      margin: 0 0 var(--nb-space-5);
      font-size: var(--nb-font-size-2xl);
      line-height: var(--nb-line-height-tight);
      font-weight: var(--nb-font-weight-bold);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignInComponent {}
