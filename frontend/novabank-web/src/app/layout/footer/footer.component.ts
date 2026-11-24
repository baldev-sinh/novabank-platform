import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'nb-footer',
  template: `
    <footer class="footer">
      <div class="footer__inner">
        <span>© {{ year }} NovaBank. Demonstration banking platform.</span>
        <span>Never share your password or a one-time code, including with NovaBank staff.</span>
      </div>
    </footer>
  `,
  styles: `
    .footer {
      background: var(--nb-color-surface);
      border-top: 1px solid var(--nb-color-divider);
    }
    .footer__inner {
      max-width: var(--nb-content-max);
      margin: 0 auto;
      padding: var(--nb-space-6) var(--nb-space-8);
      display: flex;
      flex-wrap: wrap;
      gap: var(--nb-space-3) var(--nb-space-8);
      justify-content: space-between;
      font-size: var(--nb-font-size-xs);
      color: var(--nb-color-slate);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FooterComponent {
  protected readonly year = new Date().getFullYear();
}
