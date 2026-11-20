import { TokenStorageService } from './token-storage.service';
import { inject, Injectable } from '@angular/core';
import { LoginRequest, LoginResponse } from '../models';
import { catchError, finalize, map, Observable, of, shareReplay, tap } from 'rxjs';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { Router } from '@angular/router';

/**
 * The refresh and logout endpoints are authenticated by the HttpOnly refresh token cookie and
 * require this header as a CSRF guard (Decision AE).
 */
const CSRF_HEADERS = new HttpHeaders({ 'X-Requested-With': 'XMLHttpRequest' });

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorageService = inject(TokenStorageService);
  private readonly router = inject(Router);

  private readonly authUrl = `${environment.api.baseUrl}/auth`;

  /** The refresh in progress, shared so parallel callers don't present the same token twice. */
  private refreshInFlight: Observable<string> | null = null;

  /**
   * Authenticates a user using their credentials. The server also sets the refresh token cookie,
   * which the browser only stores for a credentialed request.
   */
  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.authUrl}/login`, request, {
      withCredentials: true,
    });
  }

  /**
   * Exchanges the refresh token cookie for a new access token (the cookie is rotated too).
   * Concurrent calls share one request: the server treats a second use of the same token as theft.
   */
  refresh(): Observable<string> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<LoginResponse>(
          `${this.authUrl}/refresh`,
          {},
          { withCredentials: true, headers: CSRF_HEADERS },
        )
        .pipe(
          map((response) => response.accessToken),
          tap((accessToken) => this.tokenStorageService.saveAccessToken(accessToken)),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay({ bufferSize: 1, refCount: false }),
        );
    }
    return this.refreshInFlight;
  }

  /**
   * On start-up: signs the user back in from the refresh token cookie, if there is a usable one.
   * Never fails; without a session the user simply isn't signed in.
   */
  restoreSession(): Observable<boolean> {
    return this.refresh().pipe(
      map(() => true),
      catchError(() => of(false)),
    );
  }

  /**
   * Ends the session on the server (revokes the refresh token, clears the cookie), then locally.
   * Local state is cleared even if the server can't be reached.
   */
  logout(): void {
    this.http
      .post<void>(`${this.authUrl}/logout`, {}, { withCredentials: true, headers: CSRF_HEADERS })
      .pipe(catchError(() => of(undefined)))
      .subscribe(() => this.endSession());
  }

  /** Clears the local session and returns to the login page, without calling the server. */
  endSession(): void {
    this.tokenStorageService.clear();
    void this.router.navigate(['/login']);
  }
}
