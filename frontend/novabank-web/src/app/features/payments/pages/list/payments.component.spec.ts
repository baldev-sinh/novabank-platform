import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { payment } from '../../../accounts/accounts.testing';
import { nextLink } from '../../payments.api';
import { PaymentsComponent } from './payments.component';

describe('nextLink', () => {
  it('reads the rel="next" target of a Link header', () => {
    expect(nextLink('</api/v1/payments?limit=20&before=abc>; rel="next"')).toBe(
      '/api/v1/payments?limit=20&before=abc',
    );
    expect(nextLink(null)).toBeNull();
    expect(nextLink('</api/v1/payments?x=1>; rel="prev"')).toBeNull();
  });

  it('never follows a link out of our own API', () => {
    expect(nextLink('<https://evil.example/api/v1/payments>; rel="next"')).toBeNull();
    expect(nextLink('</api/v1x/payments>; rel="next"')).toBeNull();
  });
});

describe('PaymentsComponent', () => {
  const url = `${environment.api.baseUrl}/payments`;
  let http: HttpTestingController;

  function render() {
    localStorage.removeItem('nb.hideBalances');
    TestBed.configureTestingModule({
      imports: [PaymentsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PaymentsComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const rows = () =>
      Array.from(root.querySelectorAll('tbody tr')).map((tr) =>
        tr.textContent?.replace(/\s+/g, ' ').trim(),
      );
    return { fixture, root, rows };
  }

  afterEach(() => http.verify());

  it('lists payments with reference, status and why a failed one failed, then older pages', () => {
    const { fixture, root, rows } = render();
    const first = http.expectOne((r) => r.url === url && r.params.get('limit') === '20');
    first.flush(
      [
        payment({ paymentId: 'p1', reference: 'Rent share' }),
        payment({ paymentId: 'p2', status: 'FAILED', failureReason: 'INSUFFICIENT_FUNDS' }),
      ],
      { headers: { Link: '</api/v1/payments?limit=20&before=CURSOR>; rel="next"' } },
    );
    fixture.detectChanges();

    expect(rows()[0]).toContain('Account ending 3308');
    expect(rows()[0]).toContain('Rent share');
    expect(rows()[0]).toContain('−₹2,500.00');
    expect(rows()[0]).toContain('Completed');
    expect(rows()[1]).toContain("There wasn't enough money available.");

    (
      Array.from(root.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Show older payments'),
      ) as HTMLButtonElement
    ).click();
    http.expectOne('/api/v1/payments?limit=20&before=CURSOR').flush([payment({ paymentId: 'p3' })]);
    fixture.detectChanges();

    expect(rows()).toHaveLength(3);
    // No Link header: the last page.
    expect(root.textContent).not.toContain('Show older payments');
  });

  it('a customer without payments is offered a transfer', () => {
    const { fixture, root } = render();
    http.expectOne((r) => r.url === url).flush([]);
    fixture.detectChanges();
    expect(root.textContent).toContain("You haven't made any payments yet.");
  });
});
