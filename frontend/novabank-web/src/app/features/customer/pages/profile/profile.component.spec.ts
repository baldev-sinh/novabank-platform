import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { AuthenticationService } from '../../../auth/services/authentication.service';
import { PROFILE } from '../../customer.testing';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  const url = `${environment.api.baseUrl}/customers`;
  let http: HttpTestingController;

  function render() {
    TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthenticationService,
          useValue: { currentUser: signal({ id: 'u-1', email: 'a@b.c', roles: [] }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const type = (selector: string, value: string) => {
      const input = root.querySelector(selector) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const fill = (values: Partial<Record<string, string>> = {}) => {
      const all = {
        first: 'Priya',
        last: 'Sharma',
        day: '15',
        month: '1',
        year: '1990',
        phone: '+91 98765 43210',
        ...values,
      };
      type('input[autocomplete=given-name]', all.first);
      type('input[autocomplete=family-name]', all.last);
      type('#dob-day', all.day);
      type('#dob-month', all.month);
      type('#dob-year', all.year);
      type('input[type=tel]', all.phone);
    };
    const submit = () => {
      (root.querySelector('button[type=submit]') as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    const errors = () =>
      Array.from(root.querySelectorAll('.nb-field__error, .date__error')).map((e) =>
        e.textContent?.trim(),
      );
    return { fixture, root, fill, submit, errors, navigate };
  }

  afterEach(() => http.verify());

  it('says what is missing, without calling the server', () => {
    const { submit, errors } = render();
    submit();
    expect(errors()).toEqual([
      'Enter your first name.',
      'Enter your last name.',
      'Enter your date of birth.',
      'Enter your mobile number.',
    ]);
    http.expectNone(url);
  });

  it.each([
    [{ day: '31', month: '2' }, 'Date of birth must be a real date.'],
    [{ year: '90' }, 'Year must have 4 numbers, for example 1990.'],
    [{ day: 'x' }, 'Date of birth must include a day, month and year, in numbers.'],
    [{ year: '1850' }, 'Year must be 1900 or later.'],
    [{ year: String(new Date().getFullYear() + 1) }, 'Date of birth must be in the past.'],
  ])('checks the date of birth: %o', (date, message) => {
    const { fill, submit, errors } = render();
    fill(date);
    submit();
    expect(errors()).toEqual([message]);
    http.expectNone(url);
  });

  it('wants the phone number in international format, like the server', () => {
    const { fill, submit, errors } = render();
    fill({ phone: '98765 43210' });
    submit();
    expect(errors()[0]).toContain('international format');
    http.expectNone(url);
  });

  it('marks the three date boxes invalid together and describes the fieldset by the error', () => {
    const { root, fill, submit } = render();
    fill({ day: '31', month: '2' });
    submit();
    expect(root.querySelector('#dob-day')!.getAttribute('aria-invalid')).toBe('true');
    expect(root.querySelector('#dob-year')!.getAttribute('aria-invalid')).toBe('true');
    expect(root.querySelector('fieldset')!.getAttribute('aria-describedby')).toBe('dob-hint dob-error');
  });

  it('sends the profile with an ISO date and a normalised phone, then shows the identity check', () => {
    const { fill, submit, navigate } = render();
    fill();
    submit();

    const post = http.expectOne(url);
    expect(post.request.body).toEqual({
      firstName: 'Priya',
      lastName: 'Sharma',
      dateOfBirth: '1990-01-15',
      phone: '+919876543210',
    });
    post.flush({}, { status: 201, statusText: 'Created' });
    http.expectOne(`${url}/me`).flush(PROFILE);

    expect(navigate).toHaveBeenCalledWith('/onboarding/status');
  });

  it('a profile that already exists carries on from where onboarding is', () => {
    const { fill, submit, navigate } = render();
    fill();
    submit();
    http.expectOne(url).flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
    http.expectOne(`${url}/me`).flush({ ...PROFILE, status: 'ACTIVE' });

    expect(navigate).toHaveBeenCalledWith('/');
  });
});
