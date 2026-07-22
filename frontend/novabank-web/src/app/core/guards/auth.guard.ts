import { inject } from '@angular/core';
import { TokenStorageService } from './../../features/auth/services/token-storage.service';
import { CanActivateFn, Router } from '@angular/router';

export const authGuard: CanActivateFn = () => {
  const tokenStorageService = inject(TokenStorageService);
  const router = inject(Router);

  if (tokenStorageService.getAccessToken()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};
