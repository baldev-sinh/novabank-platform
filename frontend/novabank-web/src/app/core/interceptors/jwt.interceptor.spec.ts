import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { jwtInterceptor } from './jwt.interceptor';
import { TokenStorageService } from '../../features/auth/services/token-storage.service';
import { environment } from '../../../environments/environment';

describe('jwtInterceptor', () => {
  const api = `${environment.api.baseUrl}/accounts`;
  const refreshUrl = `${environment.api.baseUrl}/auth/refresh`;
  let client: HttpClient;
  let tokens: TokenStorageService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    client = TestBed.inject(HttpClient);
    tokens = TestBed.inject(TokenStorageService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('adds the access token to API calls', () => {
    tokens.saveAccessToken('token-1');
    client.get(api).subscribe();

    const request = http.expectOne(api);
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    request.flush([]);
  });

  it('on a 401 refreshes once and retries with the new token', () => {
    tokens.saveAccessToken('expired');
    let body: unknown;
    client.get(api).subscribe((response) => (body = response));

    http.expectOne(api).flush(null, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne(refreshUrl)
      .flush({ accessToken: 'fresh', tokenType: 'Bearer', expiresIn: 900 });
    const retry = http.expectOne(api);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer fresh');
    retry.flush(['ok']);

    expect(body).toEqual(['ok']);
  });

  it('signs out when the session can no longer be refreshed', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tokens.saveAccessToken('expired');
    client.get(api).subscribe({ error: () => undefined });

    http.expectOne(api).flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(refreshUrl).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(tokens.hasAccessToken()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('leaves the session endpoints alone', () => {
    tokens.saveAccessToken('token-1');
    client.post(refreshUrl, {}).subscribe({ error: () => undefined });

    const request = http.expectOne(refreshUrl);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('never sends the token outside the API', () => {
    tokens.saveAccessToken('token-1');
    client.get('https://other.example/api/v1/accounts').subscribe();
    client.get('/api/v1x/accounts').subscribe();

    const outside = http.expectOne('https://other.example/api/v1/accounts');
    expect(outside.request.headers.has('Authorization')).toBe(false);
    outside.flush([]);
    const lookalike = http.expectOne('/api/v1x/accounts');
    expect(lookalike.request.headers.has('Authorization')).toBe(false);
    lookalike.flush([]);
  });
});
