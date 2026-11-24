import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { AuthenticationService } from '../../../auth/services/authentication.service';
import { PROFILE } from '../../customer.testing';
import { KycStatusComponent } from './kyc-status.component';

describe('KycStatusComponent', () => {
  const me = `${environment.api.baseUrl}/customers/me`;
  let http: HttpTestingController;

  function render() {
    TestBed.configureTestingModule({
      imports: [KycStatusComponent],
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
    const fixture = TestBed.createComponent(KycStatusComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const check = () => {
      (root.querySelector('button') as HTMLButtonElement).click();
      fixture.detectChanges();
    };
    return { fixture, root, check, navigate };
  }

  afterEach(() => http.verify());

  it('still pending: says so and stays', () => {
    const { fixture, root, check, navigate } = render();
    check();
    http.expectOne(me).flush(PROFILE);
    fixture.detectChanges();

    expect(root.textContent).toContain('Not approved yet.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('approved: goes to the accounts', () => {
    const { check, navigate } = render();
    check();
    http.expectOne(me).flush({ ...PROFILE, status: 'ACTIVE' });

    expect(navigate).toHaveBeenCalledWith('/');
  });

  it('cannot check: says so', () => {
    const { fixture, root, check } = render();
    check();
    http.expectOne(me).flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    expect(root.textContent).toContain("We couldn't check right now.");
  });
});
