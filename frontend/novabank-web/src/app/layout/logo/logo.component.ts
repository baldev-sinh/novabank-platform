import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** The NovaBank mark and wordmark, linking to `link` (the accounts overview, or sign in). */
@Component({
  selector: 'nb-logo',
  imports: [RouterLink],
  template: `
    <a class="logo" [routerLink]="link()" [attr.aria-label]="label()">
      <svg width="32" height="32" viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="7" fill="currentColor" />
        <path
          d="M9 23V9l14 14V9"
          fill="none"
          stroke="#fff"
          stroke-width="2.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <span class="logo__word">NovaBank</span>
    </a>
  `,
  styles: `
    .logo {
      display: inline-flex;
      align-items: center;
      gap: var(--nb-space-3);
      color: var(--nb-color-brand);
      text-decoration: none;
    }
    .logo__word {
      color: var(--nb-color-ink);
      font-size: var(--nb-font-size-xl);
      font-weight: var(--nb-font-weight-bold);
      letter-spacing: -0.01em;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LogoComponent {
  readonly link = input<string>('/');
  readonly label = input<string>('NovaBank home');
}
