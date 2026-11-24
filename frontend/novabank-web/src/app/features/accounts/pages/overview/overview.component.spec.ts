import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { BalanceVisibility } from '../../balance-visibility.service';
import { balanceOf, CURRENT, payment, SAVINGS } from '../../accounts.testing';
import { OverviewComponent } from './overview.component';

describe('OverviewComponent', () => {
  const api = environment.api.baseUrl;
  let http: HttpTestingController;

  function render() {
    localStorage.removeItem('nb.hideBalances');
    TestBed.configureTestingModule({
      imports: [OverviewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(OverviewComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const text = () => root.textContent?.replace(/\s+/g, ' ') ?? '';
    return { fixture, root, text };
  }

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function flushAccounts(accounts = [CURRENT, SAVINGS]) {
    http.expectOne(`${api}/accounts`).flush(accounts);
  }

  it('shows each account and the exact total of what is available', () => {
    const { fixture, text } = render();
    flushAccounts();
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '162430.40', '2500.00'));
    http.expectOne(`${api}/accounts/a-2/balance`).flush(balanceOf('a-2', '21820.00'));
    http.expectOne(`${api}/payments`).flush([]);
    fixture.detectChanges();

    expect(text()).toContain('₹1,84,250.40');
    expect(text()).toContain('2 active accounts');
    expect(text()).toContain('Current account');
    expect(text()).toContain('Account ending 9012');
    expect(text()).toContain('On hold ₹2,500.00');
    expect(text()).toContain('No payments yet.');
  });

  it('hides every amount on request, and remembers it', () => {
    const { fixture, root, text } = render();
    flushAccounts([CURRENT]);
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '500.00'));
    http.expectOne(`${api}/payments`).flush([payment()]);
    fixture.detectChanges();

    const toggle = Array.from(root.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Hide balances'),
    )!;
    toggle.click();
    fixture.detectChanges();

    expect(text()).not.toContain('₹');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(localStorage.getItem('nb.hideBalances')).toBe('1');
    expect(TestBed.inject(BalanceVisibility).hidden()).toBe(true);
  });

  it('a new customer is offered their first account', () => {
    const { fixture, text } = render();
    flushAccounts([]);
    http.expectOne(`${api}/payments`).flush([]);
    fixture.detectChanges();

    expect(text()).toContain("You don't have an account yet");
  });

  it('explains a failed payment in words', () => {
    const { fixture, text } = render();
    flushAccounts([CURRENT]);
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '10.00'));
    http
      .expectOne(`${api}/payments`)
      .flush([payment({ status: 'FAILED', failureReason: 'INSUFFICIENT_FUNDS' }), payment({ paymentId: 'p2', status: 'FUNDS_HELD' })]);
    fixture.detectChanges();

    expect(text()).toContain('Not completed');
    expect(text()).toContain("There wasn't enough money available.");
    expect(text()).toContain('Processing');
  });

  it('keeps checking a new account until the ledger confirms it', () => {
    vi.useFakeTimers();
    const { fixture, text } = render();
    flushAccounts([{ ...CURRENT, status: 'PENDING' }]);
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '0.00'));
    http.expectOne(`${api}/payments`).flush([]);
    fixture.detectChanges();
    expect(text()).toContain('Being set up');

    vi.advanceTimersByTime(3000);
    flushAccounts([CURRENT]);
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '0.00'));
    fixture.detectChanges();
    expect(text()).toContain('Active');

    vi.advanceTimersByTime(10000);
    http.expectNone(`${api}/accounts`);
  });

  it('a failure to load says so and can be retried', () => {
    const { fixture, root, text } = render();
    http
      .expectOne(`${api}/accounts`)
      .flush({ title: 'Error', status: 500, traceId: 'trace-1' }, { status: 500, statusText: 'Error' });
    http.expectOne(`${api}/payments`).flush([]);
    fixture.detectChanges();
    expect(text()).toContain("We couldn't load your accounts.");
    expect(text()).toContain('trace-1');

    (Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes('Try again')) as HTMLButtonElement).click();
    flushAccounts([]);
    http.expectOne(`${api}/payments`).flush([]);
    fixture.detectChanges();
    expect(text()).toContain("You don't have an account yet");
  });
});
