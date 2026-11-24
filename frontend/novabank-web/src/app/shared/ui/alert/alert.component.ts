import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tone } from '../badge/badge.component';

/**
 * A message in the page: errors interrupt (`role="alert"`, read out at once), everything else is
 * polite (`role="status"`). Use the title for the outcome ("Transfer not completed.") and the body
 * for what it means for the customer ("Nothing was taken from your account.").
 */
@Component({
  selector: 'nb-alert',
  template: `
    <svg
      class="nb-alert__icon"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (tone()) {
        @case ('success') {
          <circle cx="12" cy="12" r="9" />
          <path d="M8 12.5l2.5 2.5L16 9.5" />
        }
        @case ('danger') {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 8v5M12 16.5v.5" />
        }
        @case ('attention') {
          <path d="M12 4l9 16H3l9-16z" />
          <path d="M12 10v4M12 17v.5" />
        }
        @default {
          <circle cx="12" cy="12" r="9" />
          <path d="M12 11v5M12 7.5v.5" />
        }
      }
    </svg>
    <div class="nb-alert__body">
      @if (title()) {
        <strong class="nb-alert__title">{{ title() }}</strong>
      }
      <ng-content />
    </div>
  `,
  styles: `
    :host {
      display: flex;
      gap: var(--nb-space-3);
      padding: var(--nb-space-3) var(--nb-space-4);
      border-radius: var(--nb-radius-control);
      font-size: var(--nb-font-size-sm);
      line-height: 1.5;
    }
    .nb-alert__icon {
      flex: none;
      margin-top: 1px;
    }
    .nb-alert__title {
      font-weight: var(--nb-font-weight-semibold);
      margin-right: var(--nb-space-1);
    }
    :host([data-tone='danger']) {
      background: var(--nb-color-danger-subtle);
      color: var(--nb-color-danger-text);
    }
    :host([data-tone='danger']) .nb-alert__icon {
      color: var(--nb-color-danger);
    }
    :host([data-tone='success']) {
      background: var(--nb-color-success-subtle);
      color: var(--nb-color-success);
    }
    :host([data-tone='attention']) {
      background: var(--nb-color-attention-subtle);
      color: var(--nb-color-attention);
    }
    :host([data-tone='info']),
    :host([data-tone='neutral']) {
      background: var(--nb-color-brand-subtle);
      color: var(--nb-color-info-text);
    }
    :host([data-tone='info']) .nb-alert__icon {
      color: var(--nb-color-brand);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-tone]': 'tone()',
    '[attr.role]': 'role()',
  },
})
export class AlertComponent {
  readonly tone = input<Tone>('info');
  readonly title = input<string | null>(null);

  protected readonly role = computed(() => (this.tone() === 'danger' ? 'alert' : 'status'));
}
