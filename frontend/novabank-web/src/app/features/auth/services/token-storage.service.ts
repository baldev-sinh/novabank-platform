import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class TokenStorageService {
  private readonly accessTokenKey = 'accessToken';

  saveAccessToken(token: string): void {
    localStorage.setItem(this.accessTokenKey, token);
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.accessTokenKey);
  }

  removeAccessToken(): void {
    localStorage.removeItem(this.accessTokenKey);
  }

  clear(): void {
    // Avoid localStorage.clear() to preserve unrelated application data.
    this.removeAccessToken();
  }
}
