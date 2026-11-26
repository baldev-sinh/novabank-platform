import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FooterComponent } from '../footer/footer.component';
import { LogoComponent } from '@novabank/ui';

/** Around the signed-out pages: sign in, register, verify email. */
@Component({
  selector: 'nb-public-layout',
  imports: [RouterOutlet, LogoComponent, FooterComponent],
  template: `
    <header class="bar">
      <div class="bar__inner">
        <nb-logo link="/login" label="NovaBank, sign in" />
        <span class="bar__secure">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <rect x="4" y="11" width="16" height="10" rx="2" />
            <path d="M8 11V7a4 4 0 0 1 8 0v4" />
          </svg>
          Secure sign-in
        </span>
      </div>
    </header>
    <main id="main" class="main">
      <router-outlet />
    </main>
    <nb-footer />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }
    .bar {
      background: var(--nb-color-surface);
      border-bottom: 1px solid var(--nb-color-divider);
    }
    .bar__inner {
      max-width: var(--nb-content-max);
      margin: 0 auto;
      padding: 0 var(--nb-space-8);
      min-height: var(--nb-header-height);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--nb-space-4);
      flex-wrap: wrap;
    }
    .bar__secure {
      display: inline-flex;
      align-items: center;
      gap: var(--nb-space-2);
      font-size: var(--nb-font-size-sm);
      color: var(--nb-color-slate);
    }
    .bar__secure svg {
      color: var(--nb-color-success-icon);
    }
    .main {
      flex: 1;
      padding: var(--nb-space-16) var(--nb-space-6);
    }
    @media (max-width: 40rem) {
      .bar__inner {
        padding: 0 var(--nb-space-5);
      }
      .main {
        padding: var(--nb-space-8) var(--nb-space-4);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicLayoutComponent {}
