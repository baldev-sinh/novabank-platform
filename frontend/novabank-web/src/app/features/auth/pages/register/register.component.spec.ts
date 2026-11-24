import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  const registerUrl = `${environment.api.baseUrl}/users/register`;
  let http: HttpTestingController;

  function render() {
    TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const type = (selector: string, value: string) => {
      const input = root.querySelector(selector) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const submit = () => {
      (root.querySelector('button[type=submit]') as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    const errorTexts = () =>
      Array.from(root.querySelectorAll('.nb-field__error')).map((e) => e.textContent?.trim());
    return { fixture, root, type, submit, errorTexts };
  }

  afterEach(() => http.verify());

  it("explains the password rule up front and counts what's missing", () => {
    const { root, type, submit, errorTexts } = render();
    expect(root.textContent).toContain('15 to 64 characters');

    type('input[type=email]', 'priya@example.com');
    type('input[autocomplete=new-password]', 'too short');
    submit();

    expect(errorTexts()).toEqual(['Use at least 15 characters. You have 9.']);
    http.expectNone(registerUrl);
  });

  it('registers and moves on to "check your email" with the address', () => {
    const { type, submit } = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    type('input[type=email]', 'priya@example.com');
    type('input[autocomplete=new-password]', 'correct horse battery staple');
    submit();

    const request = http.expectOne(registerUrl);
    expect(request.request.body).toEqual({
      email: 'priya@example.com',
      password: 'correct horse battery staple',
    });
    request.flush({}, { status: 201, statusText: 'Created' });

    expect(navigate).toHaveBeenCalledWith(['/register/check-email'], {
      state: { email: 'priya@example.com' },
    });
  });

  it('an email that already has an account: says so on the field, cleared on typing', () => {
    const { fixture, type, submit, errorTexts } = render();
    type('input[type=email]', 'taken@example.com');
    type('input[autocomplete=new-password]', 'correct horse battery staple');
    submit();
    http.expectOne(registerUrl).flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(errorTexts()[0]).toContain('There is already an account with this email address.');

    type('input[type=email]', 'other@example.com');
    expect(errorTexts()).toEqual([]);
  });

  it("shows the server's field errors next to the fields", () => {
    const { fixture, type, submit, errorTexts } = render();
    type('input[type=email]', 'priya@example.com');
    type('input[autocomplete=new-password]', 'correct horse battery staple');
    submit();
    http.expectOne(registerUrl).flush(
      { title: 'Bad Request', status: 400, errors: [{ field: 'password', message: 'Too common' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(errorTexts()).toEqual(['Too common']);
  });
});
