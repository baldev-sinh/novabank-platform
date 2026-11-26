import { inject } from '@angular/core';
import { TokenStorageService } from './../../features/auth/services/token-storage.service';
import { CanActivateFn, Router } from '@angular/router';

export const guestGuard: CanActivateFn = () => {
  const tokenStorageService = inject(TokenStorageService);
  const router = inject(Router);

  if (tokenStorageService.hasAccessToken()) {
    return router.createUrlTree(['/']);
  }

  return true;
};
