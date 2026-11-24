import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AlertComponent } from './alert/alert.component';
import { BadgeComponent, Tone } from './badge/badge.component';
import { CardComponent } from './card/card.component';

@Component({
  imports: [AlertComponent, BadgeComponent, CardComponent],
  template: `
    <nb-alert id="alert" [tone]="tone()" title="Transfer not completed.">
      Nothing was taken from your account.
    </nb-alert>
    <nb-badge id="badge" tone="success">Completed</nb-badge>
    <nb-card id="card" padding="none">Table</nb-card>
  `,
})
class HostComponent {
  readonly tone = signal<Tone>('danger');
}

describe('status components', () => {
  function render() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = (id: string) => fixture.nativeElement.querySelector('#' + id) as HTMLElement;
    return { fixture, el };
  }

  it('an error alert interrupts; other alerts are polite', () => {
    const { fixture, el } = render();
    expect(el('alert').getAttribute('role')).toBe('alert');
    expect(el('alert').textContent).toContain('Transfer not completed.');
    expect(el('alert').textContent).toContain('Nothing was taken from your account.');

    fixture.componentInstance.tone.set('info');
    fixture.detectChanges();
    expect(el('alert').getAttribute('role')).toBe('status');
  });

  it("an alert's icon is decoration only", () => {
    const { el } = render();
    expect(el('alert').querySelector('svg')!.getAttribute('aria-hidden')).toBe('true');
  });

  it('badges and cards carry their variant for styling', () => {
    const { el } = render();
    expect(el('badge').dataset['tone']).toBe('success');
    expect(el('badge').textContent).toBe('Completed');
    expect(el('card').dataset['padding']).toBe('none');
  });
});
