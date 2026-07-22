import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenStorageService } from '../../features/auth/services/token-storage.service';

const AUTH_LOGIN_ENDPOINT = '/auth/login';

export const jwtInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.url.endsWith(AUTH_LOGIN_ENDPOINT)) {
    return next(request);
  }

  const tokenStorageService = inject(TokenStorageService);

  const accessToken = tokenStorageService.getAccessToken();

  if (!accessToken) {
    return next(request);
  }

  const authenticatedRequest = request.clone({
    setHeaders: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  return next(authenticatedRequest);
};
