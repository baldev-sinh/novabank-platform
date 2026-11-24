import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { TokenStorageService } from '../../features/auth/services/token-storage.service';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
import { environment } from '../../../environments/environment';

/** Authenticated by the refresh token cookie or by credentials, never by the access token. */
const SESSION_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/logout'];

const isApi = (url: string): boolean =>
  url === environment.api.baseUrl || url.startsWith(environment.api.baseUrl + '/');

const withToken = (request: HttpRequest<unknown>, token: string): HttpRequest<unknown> =>
  request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });

/**
 * Adds the access token. On a 401 (the 15-minute token expired) it refreshes once, through the
 * shared refresh, and retries; if the session can't be refreshed, the user is signed out.
 */
export const jwtInterceptor: HttpInterceptorFn = (request, next) => {
  // The token goes to our own API only, never to another origin a future request might call.
  if (!isApi(request.url) || SESSION_ENDPOINTS.some((endpoint) => request.url.endsWith(endpoint))) {
    return next(request);
  }

  const tokenStorageService = inject(TokenStorageService);
  const authenticationService = inject(AuthenticationService);
  const accessToken = tokenStorageService.getAccessToken();

  if (!accessToken) {
    return next(request);
  }

  return next(withToken(request, accessToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return authenticationService.refresh().pipe(
        switchMap((freshToken) => next(withToken(request, freshToken))),
        catchError((refreshError: unknown) => {
          authenticationService.endSession();
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
