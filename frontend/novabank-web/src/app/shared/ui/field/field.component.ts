import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  Directive,
  inject,
  input,
} from '@angular/core';

let nextId = 0;

/**
 * A labelled form control with optional hint and error text, wired for assistive technology: the
 * label targets the control, and the control's `aria-describedby` / `aria-invalid` follow the hint
 * and error. The error text is the page's to write (it knows the context: "more than the ₹21,820.00
 * available"), so it is an input, not derived from validators.
 *
 * ```html
 * <nb-field label="Amount" prefix="₹" [error]="amountError()">
 *   <input nbInput inputmode="decimal" formControlName="amount" />
 * </nb-field>
 * ```
 */
@Component({
  selector: 'nb-field',
  template: `
    <label class="nb-field__label" [for]="controlId">
      {{ label() }}
      @if (optional()) {
        <span class="nb-field__optional">(optional)</span>
      }
    </label>
    @if (hint()) {
      <span class="nb-field__hint" [id]="hintId">{{ hint() }}</span>
    }
    <div class="nb-field__control" [class.nb-field__control--prefixed]="!!prefix()">
      @if (prefix()) {
        <span class="nb-field__prefix" aria-hidden="true">{{ prefix() }}</span>
      }
      <ng-content />
    </div>
    @if (error()) {
      <span class="nb-field__error" [id]="errorId">{{ error() }}</span>
    }
  `,
  styleUrl: './field.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'nb-field' },
})
export class FieldComponent {
  readonly label = input.required<string>();
  readonly hint = input<string | null>(null);
  readonly error = input<string | null>(null);
  /** Shown before the value and hidden from screen readers (the label already says it). */
  readonly prefix = input<string | null>(null);
  readonly optional = input(false, { transform: booleanAttribute });

  readonly controlId = `nb-field-${nextId++}`;
  protected readonly hintId = `${this.controlId}-hint`;
  protected readonly errorId = `${this.controlId}-error`;

  readonly invalid = computed(() => !!this.error());
  readonly describedBy = computed(
    () =>
      [this.hint() ? this.hintId : null, this.error() ? this.errorId : null]
        .filter(Boolean)
        .join(' ') || null,
  );
}

/** The control inside an `nb-field`: takes the field's id and ARIA wiring. */
@Directive({
  selector: 'input[nbInput], select[nbInput], textarea[nbInput]',
  host: {
    class: 'nb-input',
    '[id]': 'field.controlId',
    '[attr.aria-describedby]': 'field.describedBy()',
    '[attr.aria-invalid]': 'field.invalid() || null',
  },
})
export class InputDirective {
  protected readonly field = inject(FieldComponent);
}
