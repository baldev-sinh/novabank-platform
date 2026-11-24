import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { TokenStorageService } from '../../features/auth/services/token-storage.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const run = (url: string) =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([])] }));

  it('lets a signed-in user through', () => {
    TestBed.inject(TokenStorageService).saveAccessToken('t');
    expect(run('/accounts/1')).toBe(true);
  });

  it('sends everyone else to sign in, remembering where they were going', () => {
    const tree = run('/accounts/1') as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(tree)).toBe('/login?returnUrl=%2Faccounts%2F1');
  });

  it('adds no returnUrl for the home page', () => {
    expect(TestBed.inject(Router).serializeUrl(run('/') as UrlTree)).toBe('/login');
  });
});
