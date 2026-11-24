import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent', () => {
  const usersUrl = `${environment.api.baseUrl}/users`;
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  function render(fragment: string | null) {
    TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { fragment } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => http.verify());

  it('verifies the token from the fragment, removes it from the address bar, then goes to sign in', () => {
    render('token=abc_123');

    expect(navigate).toHaveBeenCalledWith([], { replaceUrl: true, fragment: undefined });
    const request = http.expectOne(`${usersUrl}/verify-email`);
    expect(request.request.body).toEqual({ token: 'abc_123' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { verified: 1 } });
  });

  it('a used or expired link offers a new one', () => {
    const { fixture, root } = render('token=old');
    http
      .expectOne(`${usersUrl}/verify-email`)
      .flush({ title: 'Bad Request', status: 400 }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.textContent).toContain("This link doesn't work any more");

    const email = root.querySelector('input[type=email]') as HTMLInputElement;
    email.value = 'priya@example.com';
    email.dispatchEvent(new Event('input'));
    (root.querySelector('button[type=submit]') as HTMLButtonElement).click();
    http
      .expectOne(`${usersUrl}/verify-email/resend`)
      .flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(root.textContent).toContain('a new link is on its way');
  });

  it('no token at all goes straight to asking for a new link', () => {
    const { root } = render(null);
    http.expectNone(`${usersUrl}/verify-email`);
    expect(root.textContent).toContain("This link doesn't work any more");
  });
});
