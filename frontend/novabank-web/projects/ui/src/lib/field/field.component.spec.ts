import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { FieldComponent, InputDirective } from './field.component';

@Component({
  imports: [FieldComponent, InputDirective, ReactiveFormsModule],
  template: `
    <nb-field label="To account number" hint="12 digits" [error]="error()">
      <input nbInput inputmode="numeric" [formControl]="to" />
    </nb-field>
    <nb-field label="Amount" prefix="₹">
      <input nbInput inputmode="decimal" />
    </nb-field>
    <nb-field label="Reference" optional>
      <textarea nbInput></textarea>
    </nb-field>
  `,
})
class HostComponent {
  readonly error = signal<string | null>(null);
  readonly to = new FormControl('');
}

describe('FieldComponent', () => {
  function render() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const fields = Array.from(root.querySelectorAll('nb-field'));
    return { fixture, root, fields };
  }

  it('labels each control by id, with unique ids per field', () => {
    const { fields } = render();
    const ids = fields.map((field) => {
      const label = field.querySelector('label')!;
      const control = field.querySelector('.nb-input')!;
      expect(label.htmlFor).toBe(control.id);
      return control.id;
    });
    expect(new Set(ids).size).toBe(3);
  });

  it('describes the control by its hint, and by its error once there is one', () => {
    const { fixture, fields } = render();
    const input = fields[0].querySelector('input')!;
    const hint = fields[0].querySelector('.nb-field__hint')!;
    expect(input.getAttribute('aria-describedby')).toBe(hint.id);
    expect(input.getAttribute('aria-invalid')).toBeNull();

    fixture.componentInstance.error.set('Enter the 12-digit account number.');
    fixture.detectChanges();

    const error = fields[0].querySelector('.nb-field__error')!;
    expect(error.textContent).toContain('Enter the 12-digit account number.');
    expect(input.getAttribute('aria-describedby')).toBe(`${hint.id} ${error.id}`);
    expect(input.getAttribute('aria-invalid')).toBe('true');
  });

  it('has no aria-describedby when there is nothing to describe', () => {
    const { fields } = render();
    expect(fields[1].querySelector('input')!.hasAttribute('aria-describedby')).toBe(false);
  });

  it('shows a prefix to sighted users only, and marks optional fields in the label', () => {
    const { fields } = render();
    const prefix = fields[1].querySelector('.nb-field__prefix')!;
    expect(prefix.textContent).toBe('₹');
    expect(prefix.getAttribute('aria-hidden')).toBe('true');
    expect(fields[2].querySelector('label')!.textContent).toContain('(optional)');
  });

  it('still works with reactive forms', () => {
    const { fixture, fields } = render();
    const input = fields[0].querySelector('input')!;
    input.value = '502148369012';
    input.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.to.value).toBe('502148369012');
  });
});
