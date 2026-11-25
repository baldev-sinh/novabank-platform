import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CustomerProfile, CustomerService } from './customer.service';
import { PROFILE } from './customer.testing';

describe('CustomerService', () => {
  const url = `${environment.api.baseUrl}/customers`;
  let service: CustomerService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CustomerService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('no profile yet (404) is null, not an error', () => {
    let result: CustomerProfile | null | undefined;
    service.get('u-1').subscribe((profile) => (result = profile));
    http.expectOne(`${url}/me`).flush(null, { status: 404, statusText: 'Not Found' });
    expect(result).toBeNull();
  });

  it('caches per user: asks once, and asks again for a different user', () => {
    service.get('u-1').subscribe();
    http.expectOne(`${url}/me`).flush(PROFILE);
    service.get('u-1').subscribe();
    http.expectNone(`${url}/me`);
    expect(service.profile()?.firstName).toBe('Priya');

    service.get('u-2').subscribe();
    http.expectOne(`${url}/me`).flush(null, { status: 404, statusText: 'Not Found' });
    expect(service.profile()).toBeNull();
  });

  it('other errors are passed on', () => {
    let failed = false;
    service.get('u-1').subscribe({ error: () => (failed = true) });
    http.expectOne(`${url}/me`).flush(null, { status: 503, statusText: 'Unavailable' });
    expect(failed).toBe(true);
  });

  it('creates the profile, then reads it back', () => {
    let result: CustomerProfile | null | undefined;
    service
      .create('u-1', {
        firstName: 'Priya',
        lastName: 'Sharma',
        dateOfBirth: '1990-01-15',
        phone: '+919876543210',
      })
      .subscribe((profile) => (result = profile));

    const post = http.expectOne(url);
    expect(post.request.method).toBe('POST');
    expect(post.request.body.dateOfBirth).toBe('1990-01-15');
    post.flush({}, { status: 201, statusText: 'Created' });
    http.expectOne(`${url}/me`).flush(PROFILE);

    expect(result?.status).toBe('PENDING_KYC');
  });
});
