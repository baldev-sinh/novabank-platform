import { inject, Injectable } from '@angular/core';
import { LoginRequest, LoginResponse } from '../models';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthenticationService {
  private readonly http = inject(HttpClient);

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
}
