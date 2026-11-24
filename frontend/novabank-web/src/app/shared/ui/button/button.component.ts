import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'neutral' | 'destructive' | 'link';

/**
 * The one button style, on a real `<button>` or `<a>` so keyboard and screen-reader behaviour stay
 * native: `<button nbButton>`, `<a nbButton="secondary" routerLink="…">`.
 *
 * `busy` keeps the button focusable (unlike `disabled`, which drops focus mid-submit), announces it
 * with `aria-busy` and cancels the click's default action, so a submit button can't send its form
 * twice by a double-click. Money actions are form submits for that reason; a `(click)` handler on
 * the same element isn't guaranteed to run after this guard, so it checks the busy state itself.
 */
@Component({
  selector: 'button[nbButton], a[nbButton]',
  template: `
    @if (busy()) {
      <span class="nb-button__spinner" aria-hidden="true"></span>
    }
    <ng-content />
  `,
  styleUrl: './button.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nb-button',
    '[attr.data-variant]': 'resolvedVariant()',
    '[attr.data-size]': 'size()',
    '[class.nb-button--block]': 'block()',
    '[attr.aria-busy]': 'busy() || null',
    '[attr.aria-disabled]': 'busy() || null',
    '(click)': 'guard($event)',
  },
})
export class ButtonComponent {
  /** The attribute's value; empty means primary. */
  readonly variant = input<ButtonVariant | ''>('', { alias: 'nbButton' });
  readonly size = input<'md' | 'sm'>('md');
  readonly block = input(false, { transform: booleanAttribute });
  readonly busy = input(false, { transform: booleanAttribute });

  protected readonly resolvedVariant = computed(() => this.variant() || 'primary');

  protected guard(event: Event): void {
    if (this.busy()) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }
}
