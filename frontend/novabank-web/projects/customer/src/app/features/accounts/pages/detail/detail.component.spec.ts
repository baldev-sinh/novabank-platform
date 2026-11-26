import { Clipboard } from '@angular/cdk/clipboard';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { balanceOf, CURRENT, line, payment } from '../../accounts.testing';
import { DetailComponent } from './detail.component';

describe('DetailComponent', () => {
  const api = environment.api.baseUrl;
  let http: HttpTestingController;

  function render() {
    localStorage.removeItem('nb.hideBalances');
    TestBed.configureTestingModule({
      imports: [DetailComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DetailComponent);
    fixture.componentRef.setInput('accountId', 'a-1');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const text = () => root.textContent?.replace(/\s+/g, ' ') ?? '';
    const rowTexts = () =>
      Array.from(root.querySelectorAll('tbody tr')).map((tr) =>
        tr.textContent?.replace(/\s+/g, ' ').trim(),
      );
    return { fixture, root, text, rowTexts };
  }

  afterEach(() => http.verify({ ignoreCancelled: true }));

  function flushAll(
    lines = [
      line(2),
      line(1, {
        type: 'DEPOSIT',
        direction: 'CREDIT',
        amount: '1000.00',
        balanceAfter: '1000.00',
        counterpartyLast4: null,
        reference: 'Opening deposit',
      }),
    ],
    nextBefore: number | null = null,
  ) {
    http.expectOne(`${api}/accounts/a-1`).flush(CURRENT);
    http.expectOne(`${api}/accounts/a-1/balance`).flush(balanceOf('a-1', '900.00', '2500.00'));
    http
      .expectOne((r) => r.url === `${api}/accounts/a-1/transactions` && !r.params.has('before'))
      .flush({ transactions: lines, nextBefore });
    http
      .expectOne(`${api}/payments`)
      .flush([
        payment({ paymentId: 'pending', status: 'FUNDS_HELD', reference: 'Rent share' }),
        payment({ paymentId: 'done', status: 'COMPLETED' }),
        payment({ paymentId: 'other', status: 'FUNDS_HELD', fromAccountId: 'a-9' }),
      ]);
  }

  it('shows the full account number, the balances, and pending transfers above posted lines', () => {
    const { fixture, text, rowTexts } = render();
    flushAll();
    fixture.detectChanges();

    expect(text()).toContain('5021 4836 9012');
    expect(text()).toContain('Available to spend ₹900.00');
    expect(text()).toContain('On hold for pending payments ₹2,500.00');
    const rows = rowTexts();
    expect(rows).toHaveLength(3);
    expect(rows[0]).toContain('Transfer to account ending 3308');
    expect(rows[0]).toContain('Pending');
    expect(rows[0]).toContain('Rent share');
    expect(rows[1]).toContain('−₹100.00');
    expect(rows[2]).toContain('Deposit');
    expect(rows[2]).toContain('+₹1,000.00');
  });

  it('loads older lines with the cursor, and only offers it while there are more', () => {
    const { fixture, root, rowTexts } = render();
    flushAll([line(30)], 30);
    fixture.detectChanges();

    (
      Array.from(root.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Show older transactions'),
      ) as HTMLButtonElement
    ).click();
    const older = http.expectOne(
      (r) => r.url === `${api}/accounts/a-1/transactions` && r.params.get('before') === '30',
    );
    older.flush({ transactions: [line(29)], nextBefore: null });
    fixture.detectChanges();

    expect(rowTexts()).toHaveLength(3); // 1 pending + 2 posted
    expect(root.textContent).not.toContain('Show older transactions');
  });

  it('shows money in or money out only, asked of the server, and keeps it while paging', () => {
    const { fixture, root, rowTexts } = render();
    flushAll();
    fixture.detectChanges();
    const option = (label: string) =>
      Array.from(root.querySelectorAll<HTMLButtonElement>('.filters__option')).find(
        (b) => b.textContent?.trim() === label,
      )!;
    expect(option('All').getAttribute('aria-pressed')).toBe('true');

    option('Money in').click();
    http
      .expectOne(
        (r) =>
          r.url === `${api}/accounts/a-1/transactions` &&
          r.params.get('direction') === 'CREDIT' &&
          !r.params.has('before'),
      )
      .flush({
        transactions: [line(1, { type: 'DEPOSIT', direction: 'CREDIT', counterpartyLast4: null })],
        nextBefore: 1,
      });
    fixture.detectChanges();
    expect(option('Money in').getAttribute('aria-pressed')).toBe('true');
    // The pending transfer is money out: not listed under money in.
    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Deposit');

    (
      Array.from(root.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Show older transactions'),
      ) as HTMLButtonElement
    ).click();
    http
      .expectOne((r) => r.params.get('direction') === 'CREDIT' && r.params.get('before') === '1')
      .flush({ transactions: [], nextBefore: null });

    option('Money out').click();
    http
      .expectOne((r) => r.params.get('direction') === 'DEBIT')
      .flush({ transactions: [], nextBefore: null });
    fixture.detectChanges();
    // Only the pending transfer, which is money out.
    expect(rowTexts()).toHaveLength(1);
    expect(rowTexts()[0]).toContain('Pending');
  });

  it('copies the account number and says so', () => {
    const { fixture, root, text } = render();
    flushAll();
    fixture.detectChanges();
    const copy = vi.spyOn(TestBed.inject(Clipboard), 'copy').mockReturnValue(true);

    (root.querySelector('.copy') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(copy).toHaveBeenCalledWith('502148369012');
    expect(text()).toContain('Copied');
  });

  it("an unknown account (or someone else's) is not found", () => {
    const { fixture, text } = render();
    http
      .expectOne(`${api}/accounts/a-1`)
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(text()).toContain("We couldn't find this account");
    // The other three calls were cancelled with it: nothing left waiting.
    expect(http.match(() => true).every((request) => request.cancelled)).toBe(true);
    http.verify({ ignoreCancelled: true });
  });
});
