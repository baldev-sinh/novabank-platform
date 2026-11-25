import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { balanceOf, CURRENT, payment, SAVINGS } from '../../../accounts/accounts.testing';
import { TransferComponent } from './transfer.component';

describe('TransferComponent', () => {
  const api = environment.api.baseUrl;
  let http: HttpTestingController;

  function render(from?: string) {
    TestBed.configureTestingModule({
      imports: [TransferComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TransferComponent);
    if (from) {
      fixture.componentRef.setInput('from', from);
    }
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const text = () => root.textContent?.replace(/\s+/g, ' ') ?? '';
    const type = (selector: string, value: string) => {
      const input = root.querySelector(selector) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const click = (label: string) => {
      const button = Array.from(root.querySelectorAll<HTMLElement>('button, a')).find((b) =>
        b.textContent?.replace(/\s+/g, ' ').includes(label),
      );
      if (!button) throw new Error(`no button "${label}"`);
      button.click();
      fixture.detectChanges();
    };
    const errors = () =>
      Array.from(root.querySelectorAll('.nb-field__error')).map((e) => e.textContent?.trim());
    const fill = (to: string, amount: string, reference = '') => {
      type('input.to', to);
      type('input.amount', amount);
      type('input:not(.to):not(.amount):not([type=radio])', reference);
    };
    return { fixture, root, text, type, click, errors, fill };
  }

  /** Current (₹1,000.00 available) and Savings, both active. */
  function flushSources() {
    http.expectOne(`${api}/accounts`).flush([CURRENT, SAVINGS, { ...SAVINGS, accountId: 'a-3', status: 'PENDING' }]);
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '1000.00'));
    http.expectOne(`${api}/accounts/a-2/balance`).flush(balanceOf('a-2', '50.00'));
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('offers only active accounts, the first selected, or the one asked for', () => {
    const { fixture, root } = render('a-2');
    flushSources();
    fixture.detectChanges();

    const radios = Array.from(root.querySelectorAll<HTMLInputElement>('input[type=radio]'));
    expect(radios).toHaveLength(2);
    // The value lives in Angular's radio accessor, not the DOM attribute: check the label.
    expect(radios.find((r) => r.checked)!.closest('label')!.textContent).toContain('ending 4471');
  });

  it.each([
    ['5021 4836', '10', 'Enter the 12-digit account number.'],
    ['5021 4836 9012', '10', "That's the account you're paying from. Choose a different one."],
    ['123456789012', '', 'Enter an amount.'],
    ['123456789012', '0', 'Enter an amount greater than zero.'],
    ['123456789012', '1e3', 'Enter an amount in numbers, for example 2500 or 2500.50.'],
    ['123456789012', '10.505', 'Use up to 2 decimal places.'],
    ['123456789012', '1000.01', 'This is more than the money available in this account.'],
  ])('checks to=%s amount=%s before sending: %s', (to, amount, message) => {
    const { fixture, click, errors, fill } = render();
    flushSources();
    fixture.detectChanges();
    fill(to, amount);
    click('Continue');

    expect(errors()).toContain(message);
    http.expectNone(`${api}/payments`);
  });

  it('caps one transfer at the payment service limit, in the account currency', () => {
    const { fixture, click, errors, fill } = render();
    http.expectOne(`${api}/accounts`).flush([CURRENT]);
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '5000000.00'));
    fixture.detectChanges();
    fill('123456789012', '1000000.01');
    click('Continue');

    expect(errors()).toContain('The most you can send in one transfer is ₹10,00,000.00.');
  });

  it('reviews, sends once with an Idempotency-Key, and follows the payment to "sent"', () => {
    vi.useFakeTimers();
    const { fixture, text, click, fill } = render();
    flushSources();
    fixture.detectChanges();
    fill('1234 5678 9012', '250.50', ' Rent share ');
    click('Continue');
    expect(text()).toContain('Account number 1234 5678 9012');
    expect(text()).toContain('Confirm and send ₹250.50');

    click('Confirm and send');
    const post = http.expectOne(`${api}/payments`);
    expect(post.request.body).toEqual({
      fromAccountId: 'a-1',
      toAccountNumber: '123456789012',
      amount: '250.50',
      currency: 'INR',
      reference: 'Rent share',
    });
    expect(post.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    post.flush(payment({ paymentId: 'p-1', status: 'INITIATED', toAccountNumber: '123456789012' }));
    fixture.detectChanges();
    expect(text()).toContain('Sending your transfer');

    vi.advanceTimersByTime(1000);
    http.expectOne(`${api}/payments/p-1`).flush(payment({ paymentId: 'p-1', status: 'FUNDS_HELD' }));
    vi.advanceTimersByTime(1000);
    http
      .expectOne(`${api}/payments/p-1`)
      .flush(payment({ paymentId: 'p-1', status: 'COMPLETED', amount: '250.50', toAccountNumber: '123456789012' }));
    fixture.detectChanges();

    expect(text()).toContain('Transfer sent');
    expect(text()).toContain('₹250.50 has gone to account number 1234 5678 9012');
  });

  it('a payment that fails says why, in words', () => {
    const { fixture, text, click, fill } = render();
    flushSources();
    fixture.detectChanges();
    fill('123456789012', '10');
    click('Continue');
    click('Confirm and send');
    http
      .expectOne(`${api}/payments`)
      .flush(payment({ status: 'FAILED', failureReason: 'BENEFICIARY_NOT_FOUND' }));
    fixture.detectChanges();

    expect(text()).toContain('Transfer not completed');
    expect(text()).toContain("We couldn't find an account with that number.");
  });

  it('after a network error the retry sends the same key; new details get a new one', () => {
    const { fixture, text, click, fill } = render();
    flushSources();
    fixture.detectChanges();
    fill('123456789012', '10');
    click('Continue');

    click('Confirm and send');
    const first: TestRequest = http.expectOne(`${api}/payments`);
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();
    expect(text()).toContain("you won't be charged twice");

    click('Confirm and send');
    const retry = http.expectOne(`${api}/payments`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    click('Change details');
    fill('123456789012', '20');
    click('Continue');
    click('Confirm and send');
    const changed = http.expectOne(`${api}/payments`);
    expect(changed.request.headers.get('Idempotency-Key')).not.toBe(key);
    changed.flush(payment({ status: 'COMPLETED' }));
  });

  it('after 30 checks it says the transfer is still processing, not that it failed', () => {
    vi.useFakeTimers();
    const { fixture, text, click, fill } = render();
    flushSources();
    fixture.detectChanges();
    fill('123456789012', '10');
    click('Continue');
    click('Confirm and send');
    http.expectOne(`${api}/payments`).flush(payment({ paymentId: 'slow', status: 'INITIATED' }));
    for (let i = 0; i < 30; i++) {
      vi.advanceTimersByTime(1000);
      http.expectOne(`${api}/payments/slow`).flush(payment({ paymentId: 'slow', status: 'FUNDS_HELD' }));
    }
    fixture.detectChanges();

    expect(text()).toContain('Your transfer is still being processed');
    expect(text()).toContain("You don't need to send it again");
  });

  it('without an active account, offers to open one', () => {
    const { fixture, text } = render();
    http.expectOne(`${api}/accounts`).flush([{ ...CURRENT, status: 'PENDING' }]);
    fixture.detectChanges();

    expect(text()).toContain('You need an active account to send money');
  });
});
