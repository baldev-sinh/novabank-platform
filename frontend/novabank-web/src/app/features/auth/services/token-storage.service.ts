import { Injectable } from '@angular/core';

/**
 * Holds the access token in memory only (Decision AE). Nothing is written to localStorage, so an
 * injected script finds no token to steal there; after a reload the session is restored from the
 * HttpOnly refresh token cookie (AuthenticationService.restoreSession).
 */
@Injectable({
  providedIn: 'root',
})
export class TokenStorageService {
  private accessToken: string | null = null;

  saveAccessToken(token: string): void {
    this.accessToken = token;
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  removeAccessToken(): void {
    this.accessToken = null;
  }

  clear(): void {
    this.removeAccessToken();
  }

  hasAccessToken(): boolean {
    return this.accessToken !== null;
  }
}
