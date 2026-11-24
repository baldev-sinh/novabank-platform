import { TokenStorageService } from './token-storage.service';
import { inject, Injectable, signal } from '@angular/core';
import { AuthenticatedUser, LoginRequest, LoginResponse, RegisterRequest, Role } from '../models';
import { catchError, finalize, map, Observable, of, shareReplay, switchMap, tap } from 'rxjs';
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
  private readonly usersUrl = `${environment.api.baseUrl}/users`;

  private readonly user = signal<AuthenticatedUser | null>(null);
  /** The signed-in user, or null. */
  readonly currentUser = this.user.asReadonly();

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
   * Signs in: credentials for an access token (kept in memory), then the user's own details.
   * Errors are the server's problems as they come (e.g. 403 email-not-verified).
   */
  signIn(request: LoginRequest): Observable<AuthenticatedUser> {
    return this.login(request).pipe(
      tap((response) => this.tokenStorageService.saveAccessToken(response.accessToken)),
      switchMap(() => this.loadCurrentUser()),
    );
  }

  /** Creates the user; the server emails a verification link. */
  register(request: RegisterRequest): Observable<void> {
    return this.http.post<void>(`${this.usersUrl}/register`, request);
  }

  /** The token from the emailed link (its URL fragment, so it never reaches a server log). */
  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>(`${this.usersUrl}/verify-email`, { token });
  }

  /** Always accepted by the server, whether or not the address has an account (no enumeration). */
  resendVerification(email: string): Observable<void> {
    return this.http.post<void>(`${this.usersUrl}/verify-email/resend`, { email });
  }

  loadCurrentUser(): Observable<AuthenticatedUser> {
    return this.http
      .get<{ userId: string; email: string; roles: Role[] }>(`${this.authUrl}/me`)
      .pipe(
        map((me) => ({ id: me.userId, email: me.email, roles: me.roles })),
        tap((user) => this.user.set(user)),
      );
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
      switchMap(() => this.loadCurrentUser()),
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
    this.user.set(null);
    void this.router.navigate(['/login']);
  }
}
