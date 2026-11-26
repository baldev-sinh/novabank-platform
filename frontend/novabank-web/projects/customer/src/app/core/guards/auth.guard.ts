import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenStorageService } from '../../features/auth/services/token-storage.service';

/** Signed-in pages only; otherwise to sign in, coming back here afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(TokenStorageService).hasAccessToken()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], {
    queryParams: state.url && state.url !== '/' ? { returnUrl: state.url } : {},
  });
};
