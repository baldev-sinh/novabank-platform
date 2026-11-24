import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { CURRENT } from '../../accounts.testing';
import { OpenAccountComponent } from './open-account.component';

describe('OpenAccountComponent', () => {
  const url = `${environment.api.baseUrl}/accounts`;
  let http: HttpTestingController;

  function render() {
    TestBed.configureTestingModule({
      imports: [OpenAccountComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(OpenAccountComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const submit = () => {
      (root.querySelector('button[type=submit]') as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    return { fixture, root, submit, navigate };
  }

  afterEach(() => http.verify());

  it('opens the chosen account and shows it', () => {
    const { root, submit, navigate } = render();
    (root.querySelector('input[value=SAVINGS]') as HTMLInputElement).click();
    const select = root.querySelector('select') as HTMLSelectElement;
    select.value = 'EUR';
    select.dispatchEvent(new Event('change'));
    submit();

    const request = http.expectOne(url);
    expect(request.request.body).toEqual({ type: 'SAVINGS', currency: 'EUR' });
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush({ ...CURRENT, accountId: 'new', status: 'PENDING' });

    expect(navigate).toHaveBeenCalledWith(['/accounts', 'new']);
  });

  it('a retry after a failure sends the same Idempotency-Key, so only one account opens', () => {
    const { fixture, root, submit } = render();
    submit();
    const first = http.expectOne(url);
    const key = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();
    expect(root.textContent).toContain('You won’t end up with two accounts.');

    submit();
    const second = http.expectOne(url);
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush(CURRENT);
  });

  it('a customer just approved, before the account service knows, is asked to wait a moment', () => {
    const { fixture, root, submit } = render();
    submit();
    http.expectOne(url).flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(root.textContent).toContain("We're still setting up your banking.");
  });
});
