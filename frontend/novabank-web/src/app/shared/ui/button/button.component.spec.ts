import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ButtonComponent } from './button.component';

@Component({
  imports: [ButtonComponent],
  template: `
    <form (submit)="submitted = submitted + 1; $event.preventDefault()">
      <button id="submit" nbButton type="submit" [busy]="busy()">Send</button>
    </form>
    <button id="secondary" nbButton="secondary" size="sm" block>Cancel</button>
    <a id="link" nbButton="neutral" href="/accounts">Accounts</a>
  `,
})
class HostComponent {
  readonly busy = signal(false);
  submitted = 0;
}

describe('ButtonComponent', () => {
  function render() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = (id: string) => fixture.nativeElement.querySelector('#' + id) as HTMLElement;
    return { fixture, el };
  }

  it('styles real buttons and links, primary by default', () => {
    const { el } = render();
    expect(el('submit').dataset['variant']).toBe('primary');
    expect(el('submit').tagName).toBe('BUTTON');
    expect(el('secondary').dataset['variant']).toBe('secondary');
    expect(el('secondary').dataset['size']).toBe('sm');
    expect(el('secondary').classList).toContain('nb-button--block');
    expect(el('link').tagName).toBe('A');
    expect(el('link').getAttribute('href')).toBe('/accounts');
  });

  it('submits normally when not busy', () => {
    const { fixture, el } = render();
    el('submit').click();
    expect(fixture.componentInstance.submitted).toBe(1);
    expect(el('submit').getAttribute('aria-busy')).toBeNull();
  });

  it('while busy stays focusable, says so, and does not submit again', () => {
    const { fixture, el } = render();
    fixture.componentInstance.busy.set(true);
    fixture.detectChanges();

    const button = el('submit') as HTMLButtonElement;
    expect(button.disabled).toBe(false);
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.querySelector('.nb-button__spinner')?.getAttribute('aria-hidden')).toBe('true');

    button.click();
    button.click();
    expect(fixture.componentInstance.submitted).toBe(0);
  });
});
