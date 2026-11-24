import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** A white panel on the page canvas. `padding="none"` for content that runs edge to edge (tables). */
@Component({
  selector: 'nb-card',
  template: '<ng-content />',
  styles: `
    :host {
      display: block;
      background: var(--nb-color-surface);
      border: 1px solid var(--nb-color-divider);
      border-radius: var(--nb-radius-card);
      box-shadow: var(--nb-shadow-card);
    }
    :host([data-padding='md']) {
      padding: var(--nb-space-6) var(--nb-space-7);
    }
    :host([data-padding='lg']) {
      padding: var(--nb-space-8);
    }
    :host([data-padding='none']) {
      overflow: hidden;
    }
    @media (max-width: 40rem) {
      :host([data-padding='md']),
      :host([data-padding='lg']) {
        padding: var(--nb-space-5);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-padding]': 'padding()' },
})
export class CardComponent {
  readonly padding = input<'none' | 'md' | 'lg'>('md');
}
