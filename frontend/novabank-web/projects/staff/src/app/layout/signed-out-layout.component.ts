import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LogoComponent } from '@novabank/ui';

/**
 * Around the signed-out staff pages. The dark strip on every back-office screen says where you are,
 * so a staff member never mistakes it for the customer site (approved design, Decision AR).
 */
@Component({
  selector: 'nb-signed-out-layout',
  imports: [RouterOutlet, LogoComponent],
  template: `
    <div class="strip">
      <div class="strip__inner">
        <span>NovaBank staff back-office · Internal</span>
        <span class="strip__note">Activity is recorded in the audit log</span>
      </div>
    </div>
    <main id="main" class="main">
      <div class="panel">
        <div class="brand">
          <nb-logo link="/sign-in" label="NovaBank back-office, sign in" />
          <span class="brand__tag">Staff</span>
        </div>
        <router-outlet />
        <p class="notice" role="note">
          For staff only. Two-step sign-in is required for every staff account. Never share a code,
          even with colleagues.
        </p>
      </div>
    </main>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }
    .strip {
      background: var(--nb-color-ink);
      color: var(--nb-color-on-brand);
      font-size: var(--nb-font-size-xs);
      font-weight: var(--nb-font-weight-semibold);
    }
    .strip__inner {
      max-width: var(--nb-content-max);
      margin: 0 auto;
      padding: var(--nb-space-2) var(--nb-space-8);
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: var(--nb-space-2) var(--nb-space-4);
    }
    .strip__note {
      font-weight: var(--nb-font-weight-medium);
    }
    .main {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: flex-start;
      padding: var(--nb-space-16) var(--nb-space-6);
    }
    .panel {
      width: 100%;
      max-width: 27.5rem;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
      gap: var(--nb-space-5);
      padding: var(--nb-space-10);
      background: var(--nb-color-surface);
      border: 1px solid var(--nb-color-divider);
      border-radius: var(--nb-radius-card);
      box-shadow: var(--nb-shadow-card);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: var(--nb-space-3);
    }
    .brand__tag {
      padding: 0.1875rem var(--nb-space-2);
      border-radius: var(--nb-radius-pill);
      background: var(--nb-color-brand-subtle);
      color: var(--nb-color-brand);
      font-size: 0.75rem;
      font-weight: var(--nb-font-weight-semibold);
    }
    .notice {
      margin: 0;
      padding: var(--nb-space-3) var(--nb-space-4);
      border-radius: var(--nb-radius-control);
      background: var(--nb-color-canvas);
      color: var(--nb-color-ink-soft);
      font-size: var(--nb-font-size-xs);
      line-height: 1.45;
    }
    @media (max-width: 40rem) {
      .strip__inner {
        padding: var(--nb-space-2) var(--nb-space-5);
      }
      .main {
        padding: var(--nb-space-8) var(--nb-space-4);
      }
      .panel {
        padding: var(--nb-space-6);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignedOutLayoutComponent {}
