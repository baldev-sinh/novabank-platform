import type { DialogRef } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { DOCUMENT, inject, Injectable, Injector } from '@angular/core';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
import type { SessionWarningChoice } from './session-warning.component';

/**
 * Auth ends a sign-in 30 minutes after its last refresh (`refresh-token.idle-timeout`, Decision AE),
 * whatever happens on the page. So the app keeps the two in step:
 * - while the customer is active, it renews the sign-in now and then, so reading a long history
 *   never ends in a surprise sign-out;
 * - after 25 minutes without activity it warns, and at 30 it signs out and says why.
 * The limits must stay at or under the server's.
 */
export const IDLE_SIGN_OUT_MS = 30 * 60_000;
export const WARNING_BEFORE_MS = 5 * 60_000;
/** Renew the sign-in on activity once it is this old (well inside the server's 30 minutes). */
export const RENEW_AFTER_MS = 10 * 60_000;
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const;

@Injectable({ providedIn: 'root' })
export class IdleSession {
  private readonly auth = inject(AuthenticationService);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);

  private lastActivity = 0;
  private warningTimer: ReturnType<typeof setTimeout> | null = null;
  private signOutTimer: ReturnType<typeof setTimeout> | null = null;
  private warning: DialogRef<SessionWarningChoice> | null = null;
  private renewing = false;
  private running = false;

  private readonly onActivity = (): void => this.activity();

  /** From the signed-in layout; idempotent. */
  start(): void {
    if (this.running) {
      return;
    }
    this.running = true;
    for (const event of ACTIVITY_EVENTS) {
      this.document.addEventListener(event, this.onActivity, { passive: true, capture: true });
    }
    this.activity();
  }

  stop(): void {
    this.running = false;
    for (const event of ACTIVITY_EVENTS) {
      this.document.removeEventListener(event, this.onActivity, { capture: true });
    }
    this.clearTimers();
    this.warning?.close();
    this.warning = null;
  }

  private activity(): void {
    // While the warning is open only its buttons count: a stray mouse move mustn't hide it.
    if (this.warning) {
      return;
    }
    const now = Date.now();
    // Many events per second while scrolling or typing: reschedule at most once a second.
    if (now - this.lastActivity < 1000) {
      return;
    }
    this.lastActivity = now;
    this.schedule();
    if (now - this.auth.lastRenewedAt() > RENEW_AFTER_MS) {
      this.renew();
    }
  }

  private schedule(): void {
    this.clearTimers();
    this.warningTimer = setTimeout(() => void this.warn(), IDLE_SIGN_OUT_MS - WARNING_BEFORE_MS);
    this.signOutTimer = setTimeout(() => this.signOut(), IDLE_SIGN_OUT_MS);
  }

  /**
   * The dialog code (CDK overlay + the component) is loaded only when a warning is due, so it
   * isn't in the bundle every signed-in page downloads.
   */
  private async warn(): Promise<void> {
    const [{ Dialog }, { SessionWarningComponent }] = await Promise.all([
      import('@angular/cdk/dialog'),
      import('./session-warning.component'),
    ]);
    if (!this.running) {
      return;
    }
    this.warning = this.injector.get(Dialog).open<SessionWarningChoice>(SessionWarningComponent, {
      role: 'alertdialog',
      ariaModal: true,
      ariaLabelledBy: 'session-warning-title',
      ariaDescribedBy: 'session-warning-text',
      disableClose: true,
      autoFocus: 'first-tabbable',
      data: { deadline: this.lastActivity + IDLE_SIGN_OUT_MS },
    });
    this.warning.closed.subscribe((choice) => {
      this.warning = null;
      if (choice === 'stay') {
        this.stay();
      } else if (choice === 'sign-out') {
        this.stop();
        this.auth.logout();
      }
    });
  }

  private stay(): void {
    this.lastActivity = 0;
    this.activity();
    this.renew();
  }

  private renew(): void {
    if (this.renewing) {
      return;
    }
    this.renewing = true;
    this.auth.refresh().subscribe({
      next: () => (this.renewing = false),
      error: (error: unknown) => {
        this.renewing = false;
        // Refused: the 8-hour limit, or a revoked sign-in, leaves nothing to stay in. A network
        // blip is not a reason to sign anyone out; the next activity tries again.
        if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
          this.stop();
          this.auth.endSession('expired');
        }
      },
    });
  }

  private signOut(): void {
    this.stop();
    this.auth.logout('idle');
  }

  private clearTimers(): void {
    if (this.warningTimer) clearTimeout(this.warningTimer);
    if (this.signOutTimer) clearTimeout(this.signOutTimer);
    this.warningTimer = this.signOutTimer = null;
  }
}
