import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, Observable, of, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

/** PENDING_KYC until compliance approves the identity check; only then can money move. */
export type CustomerStatus = 'PENDING_KYC' | 'ACTIVE';

export interface CustomerProfile {
  customerId: string;
  userId: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  phone: string;
  status: CustomerStatus;
}

export interface NewCustomerProfile {
  firstName: string;
  lastName: string;
  /** ISO date, YYYY-MM-DD. */
  dateOfBirth: string;
  /** International format, e.g. +919876543210. */
  phone: string;
}

/**
 * The signed-in customer's profile (customer service). Cached per user: the onboarding guard asks
 * on every signed-in navigation, and a different user (after a sign-out) never sees the previous
 * one's profile.
 */
@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.api.baseUrl}/customers`;

  private readonly cached = signal<{ userId: string; profile: CustomerProfile | null } | null>(
    null,
  );

  /** The profile for `userId` if already loaded; `undefined` if not loaded yet. */
  cachedFor(userId: string): CustomerProfile | null | undefined {
    const cached = this.cached();
    return cached?.userId === userId ? cached.profile : undefined;
  }

  /** The loaded profile of whoever is signed in, for display (header name). */
  readonly profile = () => this.cached()?.profile ?? null;

  /** `null` when the user hasn't created a profile yet (404). */
  load(userId: string): Observable<CustomerProfile | null> {
    return this.http.get<CustomerProfile>(`${this.url}/me`).pipe(
      catchError((error: unknown) =>
        error instanceof HttpErrorResponse && error.status === 404
          ? of(null)
          : throwError(() => error),
      ),
      tap((profile) => this.cached.set({ userId, profile })),
    );
  }

  /** Cached profile, or loads it. */
  get(userId: string): Observable<CustomerProfile | null> {
    const cached = this.cachedFor(userId);
    return cached !== undefined ? of(cached) : this.load(userId);
  }

  /** Creates the profile (PENDING_KYC), then reads it back in full. */
  create(userId: string, profile: NewCustomerProfile): Observable<CustomerProfile | null> {
    return this.http.post<unknown>(this.url, profile).pipe(switchMap(() => this.load(userId)));
  }
}
