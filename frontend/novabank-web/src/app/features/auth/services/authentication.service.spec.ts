import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthenticationService } from './authentication.service';
import { TokenStorageService } from './token-storage.service';
import { environment } from '../../../../environments/environment';

describe('AuthenticationService', () => {
  const authUrl = `${environment.api.baseUrl}/auth`;
  let service: AuthenticationService;
  let tokens: TokenStorageService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthenticationService);
    tokens = TestBed.inject(TokenStorageService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends credentials and the CSRF header on refresh, and keeps the new token in memory', () => {
    let received: string | undefined;
    service.refresh().subscribe((token) => (received = token));

    const request = http.expectOne(`${authUrl}/refresh`);
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
    request.flush({ accessToken: 'fresh', tokenType: 'Bearer', expiresIn: 900 });

    expect(received).toBe('fresh');
    expect(tokens.getAccessToken()).toBe('fresh');
    expect(localStorage.getItem('accessToken')).toBeNull();
  });

  it('shares one refresh between parallel callers, so the token is presented only once', () => {
    const results: string[] = [];
    service.refresh().subscribe((token) => results.push(token));
    service.refresh().subscribe((token) => results.push(token));

    http
      .expectOne(`${authUrl}/refresh`)
      .flush({ accessToken: 'once', tokenType: 'Bearer', expiresIn: 900 });

    expect(results).toEqual(['once', 'once']);
  });

  it('restores no session when the refresh is refused, without throwing', () => {
    let restored: boolean | undefined;
    service.restoreSession().subscribe((result) => (restored = result));

    http
      .expectOne(`${authUrl}/refresh`)
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(restored).toBe(false);
    expect(tokens.hasAccessToken()).toBe(false);
  });

  it('logs out on the server, then clears the session even if the server call fails', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    tokens.saveAccessToken('current');

    service.logout();
    const request = http.expectOne(`${authUrl}/logout`);
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
    request.flush(null, { status: 500, statusText: 'Server Error' });

    expect(tokens.hasAccessToken()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
  it('signs in: keeps the token in memory, then loads the user', () => {
    let email: string | undefined;
    service
      .signIn({ email: 'priya@example.com', password: 'correct horse battery' })
      .subscribe((user) => (email = user.email));

    const login = http.expectOne(`${authUrl}/login`);
    expect(login.request.withCredentials).toBe(true);
    login.flush({ accessToken: 'token-1', tokenType: 'Bearer', expiresIn: 900 });
    expect(tokens.getAccessToken()).toBe('token-1');
    http
      .expectOne(`${authUrl}/me`)
      .flush({ userId: 'u-1', email: 'priya@example.com', roles: ['CUSTOMER'] });

    expect(email).toBe('priya@example.com');
    expect(service.currentUser()?.id).toBe('u-1');
  });

  it('a restored session loads the user too', () => {
    let restored: boolean | undefined;
    service.restoreSession().subscribe((result) => (restored = result));

    http
      .expectOne(`${authUrl}/refresh`)
      .flush({ accessToken: 'token-2', tokenType: 'Bearer', expiresIn: 900 });
    http.expectOne(`${authUrl}/me`).flush({ userId: 'u-1', email: 'a@b.c', roles: ['CUSTOMER'] });

    expect(restored).toBe(true);
    expect(service.currentUser()?.email).toBe('a@b.c');
  });

  it('ending the session forgets the user', () => {
    service.signIn({ email: 'a@b.c', password: 'x' }).subscribe();
    http.expectOne(`${authUrl}/login`).flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 1 });
    http.expectOne(`${authUrl}/me`).flush({ userId: 'u-1', email: 'a@b.c', roles: [] });

    service.endSession();

    expect(service.currentUser()).toBeNull();
    expect(tokens.hasAccessToken()).toBe(false);
  });

  it('registers, verifies and resends on the users endpoints', () => {
    const usersUrl = `${environment.api.baseUrl}/users`;
    service.register({ email: 'a@b.c', password: 'fifteen chars ok' }).subscribe();
    service.verifyEmail('tok').subscribe();
    service.resendVerification('a@b.c').subscribe();

    expect(http.expectOne(`${usersUrl}/register`).request.body).toEqual({
      email: 'a@b.c',
      password: 'fifteen chars ok',
    });
    expect(http.expectOne(`${usersUrl}/verify-email`).request.body).toEqual({ token: 'tok' });
    expect(http.expectOne(`${usersUrl}/verify-email/resend`).request.body).toEqual({
      email: 'a@b.c',
    });
  });
});
