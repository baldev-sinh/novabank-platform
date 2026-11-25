import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  const api = environment.api.baseUrl;
  let http: HttpTestingController;

  function render(query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const type = (selector: string, value: string) => {
      const input = root.querySelector(selector) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = () => {
      (root.querySelector('button[type=submit]') as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    const fillAndSubmit = () => {
      type('input[type=email]', 'priya@example.com');
      type('input[autocomplete=current-password]', 'correct horse battery');
      submit();
    };
    return { fixture, root, type, submit, fillAndSubmit };
  }

  afterEach(() => http.verify());

  it('shows what is missing on submit, next to each field, without calling the server', () => {
    const { root, submit } = render();
    submit();

    const errors = Array.from(root.querySelectorAll('.nb-field__error')).map((e) => e.textContent);
    expect(errors).toEqual(['Enter your email address.', 'Enter your password.']);
    http.expectNone(`${api}/auth/login`);
  });

  it('signs in and goes where the customer was heading', () => {
    const { fillAndSubmit } = render({ returnUrl: '/accounts/1' });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fillAndSubmit();

    http
      .expectOne(`${api}/auth/login`)
      .flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${api}/auth/me`).flush({ userId: 'u', email: 'priya@example.com', roles: [] });

    expect(navigate).toHaveBeenCalledWith('/accounts/1');
  });

  it('never follows a returnUrl to another site', () => {
    const { fillAndSubmit } = render({ returnUrl: '//evil.example/phish' });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fillAndSubmit();
    http
      .expectOne(`${api}/auth/login`)
      .flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${api}/auth/me`).flush({ userId: 'u', email: 'a@b.c', roles: [] });

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('wrong credentials: one message that does not say which part was wrong', () => {
    const { fixture, root, fillAndSubmit } = render();
    fillAndSubmit();
    http
      .expectOne(`${api}/auth/login`)
      .flush({ title: 'Unauthorized', status: 401 }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    const alert = root.querySelector('nb-alert')!;
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.textContent).toContain("The email address or password isn't right.");
  });

  it('unverified email: says so and can send the link again', () => {
    const { fixture, root, fillAndSubmit } = render();
    fillAndSubmit();
    http.expectOne(`${api}/auth/login`).flush(
      {
        type: 'urn:novabank:problem:email-not-verified',
        title: 'Email not verified',
        status: 403,
      },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(root.textContent).toContain('Verify your email address first.');

    (root.querySelector('nb-alert button') as HTMLButtonElement).click();
    const resend = http.expectOne(`${api}/users/verify-email/resend`);
    expect(resend.request.body).toEqual({ email: 'priya@example.com' });
    resend.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(root.textContent).toContain('a new link is on its way');
  });

  it('too many attempts: says how long to wait, from Retry-After', () => {
    const { fixture, root, fillAndSubmit } = render();
    fillAndSubmit();
    http.expectOne(`${api}/auth/login`).flush(
      { type: 'urn:novabank:problem:rate-limited', title: 'Too Many Requests', status: 429 },
      {
        status: 429,
        statusText: 'Too Many Requests',
        headers: new HttpHeaders({ 'Retry-After': '300' }),
      },
    );
    fixture.detectChanges();

    expect(root.textContent).toContain('wait 5 minutes');
  });

  it('offline: says the service cannot be reached', () => {
    const { fixture, root, fillAndSubmit } = render();
    fillAndSubmit();
    http.expectOne(`${api}/auth/login`).error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();

    expect(root.textContent).toContain("We can't reach NovaBank right now.");
  });

  it('says why the app signed the customer out', () => {
    const { root } = render({ reason: 'idle' });
    expect(root.textContent).toContain("You've been signed out.");
    expect(root.textContent).toContain('30 minutes without activity');
  });

  it('confirms a just-verified email address', () => {
    const { root } = render({ verified: '1' });
    expect(root.textContent).toContain('Your email address is verified.');
  });

  it('shows and hides the password, saying which state it is in', () => {
    const { fixture, root } = render();
    const toggle = root.querySelector('.toggle') as HTMLButtonElement;
    const password = () =>
      root.querySelector('input[autocomplete=current-password]') as HTMLInputElement;
    expect(password().type).toBe('password');
    expect(toggle.getAttribute('aria-pressed')).toBe('false');

    toggle.click();
    fixture.detectChanges();
    expect(password().type).toBe('text');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
  });
});
