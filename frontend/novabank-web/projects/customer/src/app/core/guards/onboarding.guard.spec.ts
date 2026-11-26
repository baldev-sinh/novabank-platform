import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, isObservable, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
import { PROFILE } from '../../features/customer/customer.testing';
import { onboardingGuard, OnboardingStep } from './onboarding.guard';

describe('onboardingGuard', () => {
  const me = `${environment.api.baseUrl}/customers/me`;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: AuthenticationService,
          useValue: { currentUser: signal({ id: 'u-1', email: 'a@b.c', roles: [] }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function run(step: OnboardingStep, respond: (url: string) => void): Promise<string | true> {
    const result = TestBed.runInInjectionContext(() =>
      onboardingGuard(step)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    const pending = firstValueFrom(result as Observable<boolean | UrlTree>);
    expect(isObservable(result)).toBe(true);
    respond(me);
    const value = await pending;
    return value === true ? true : TestBed.inject(Router).serializeUrl(value as UrlTree);
  }

  const noProfile = (url: string) =>
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });
  const pending = (url: string) => http.expectOne(url).flush(PROFILE);
  const active = (url: string) => http.expectOne(url).flush({ ...PROFILE, status: 'ACTIVE' });

  it('no profile yet: everything leads to the profile form', async () => {
    expect(await run('banking', noProfile)).toBe('/onboarding/profile');
    expect(await run('profile', () => undefined)).toBe(true);
  });

  it('identity check pending: banking and the form lead to the status page', async () => {
    expect(await run('banking', pending)).toBe('/onboarding/status');
    expect(await run('profile', () => undefined)).toBe('/onboarding/status');
    expect(await run('status', () => undefined)).toBe(true);
  });

  it('approved: banking is open, onboarding pages lead home', async () => {
    expect(await run('banking', active)).toBe(true);
    expect(await run('status', () => undefined)).toBe('/');
  });

  it('if the profile cannot be loaded, navigation goes ahead (the APIs still decide)', async () => {
    expect(
      await run('banking', (url) =>
        http.expectOne(url).flush(null, { status: 503, statusText: 'Unavailable' }),
      ),
    ).toBe(true);
  });
});
