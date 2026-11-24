import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
import { CustomerProfile, CustomerService } from '../../features/customer/customer.service';

/** Where a signed-in customer belongs, by how far onboarding has got. */
export type OnboardingStep = 'profile' | 'status' | 'banking';

const PATHS: Record<OnboardingStep, string> = {
  profile: '/onboarding/profile',
  status: '/onboarding/status',
  banking: '/',
};

export function stepFor(profile: CustomerProfile | null): OnboardingStep {
  if (!profile) {
    return 'profile';
  }
  return profile.status === 'ACTIVE' ? 'banking' : 'status';
}

/**
 * Keeps each signed-in page to the customers it is for: no profile yet → the profile form; identity
 * check pending → its status page; approved → banking. Runs after authGuard.
 *
 * If the profile can't be loaded (customer service down), navigation goes ahead: this is guidance,
 * not security. Every API call is authorised by the services themselves, and the page shows the
 * error it gets.
 */
export function onboardingGuard(step: OnboardingStep): CanActivateFn {
  return () => {
    const router = inject(Router);
    const customers = inject(CustomerService);
    const user = inject(AuthenticationService).currentUser();
    if (!user) {
      return true;
    }
    return customers.get(user.id).pipe(
      map((profile) => {
        const target = stepFor(profile);
        return target === step ? true : router.parseUrl(PATHS[target]);
      }),
      catchError(() => of(true)),
    );
  };
}
