import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type Tone = 'success' | 'info' | 'attention' | 'danger' | 'neutral';

/**
 * A short status label: "Completed", "Processing", "Being set up". Colour is never the only signal;
 * the words carry the meaning, and every tone meets 4.5:1.
 */
@Component({
  selector: 'nb-badge',
  template: '<ng-content />',
  styles: `
    :host {
      display: inline-block;
      padding: 0.1875rem var(--nb-space-2);
      border-radius: var(--nb-radius-pill);
      font-size: 0.75rem;
      font-weight: var(--nb-font-weight-semibold);
      line-height: 1.4;
      white-space: nowrap;
    }
    :host([data-tone='success']) {
      background: var(--nb-color-success-subtle);
      color: var(--nb-color-success);
    }
    :host([data-tone='info']) {
      background: var(--nb-color-brand-subtle);
      color: var(--nb-color-brand);
    }
    :host([data-tone='attention']) {
      background: var(--nb-color-attention-subtle);
      color: var(--nb-color-attention);
    }
    :host([data-tone='danger']) {
      background: var(--nb-color-danger-subtle);
      color: var(--nb-color-danger-strong);
    }
    :host([data-tone='neutral']) {
      background: var(--nb-color-canvas);
      color: var(--nb-color-ink-soft);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-tone]': 'tone()' },
})
export class BadgeComponent {
  readonly tone = input<Tone>('neutral');
}
