import { TokenStorageService } from './token-storage.service';
import { inject, Injectable } from '@angular/core';
import { LoginRequest, LoginResponse } from '../models';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private readonly http = inject(HttpClient);
  private readonly tokenStorageService = inject(TokenStorageService);
  private readonly router = inject(Router);

  private readonly authUrl = `${environment.api.baseUrl}/auth`;

  /**
   * Authenticates a user using their credentials.
   *
   * @param request Login credentials.
   * @returns Observable emitting the authentication response.
   */
  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.authUrl}/login`, request);
  }

  /**
   * Logs out the current user.
   *
   * Clears the stored authentication state and navigates to the login page.
   */
  logout(): void {
    this.tokenStorageService.clear();

    void this.router.navigate(['/login']);
  }
}
