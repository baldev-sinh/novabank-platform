import { HttpErrorResponse } from '@angular/common/http';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
import { IdleSession } from './idle-session.service';

const MINUTE = 60_000;

describe('IdleSession', () => {
  let session: IdleSession;
  let refreshes: Subject<string>[];
  const auth = {
    renewedAt: 0,
    lastRenewedAt: () => auth.renewedAt,
    refresh: vi.fn(),
    logout: vi.fn(),
    endSession: vi.fn(),
  };

  beforeEach(async () => {
    // The service loads the dialog's code on demand. Load it here, before the clock is faked, so
    // the service's import() resolves from the module cache: waitFor's time limit is real time,
    // and loading the files on a busy machine could exceed it (a flaky "warns after 25 minutes").
    await Promise.all([import('@angular/cdk/dialog'), import('./session-warning.component')]);
    vi.useFakeTimers();
    refreshes = [];
    auth.renewedAt = Date.now();
    auth.refresh.mockReset().mockImplementation(() => {
      const response = new Subject<string>();
      refreshes.push(response);
      return response;
    });
    auth.logout.mockReset();
    auth.endSession.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: AuthenticationService, useValue: auth }],
    });
    session = TestBed.inject(IdleSession);
    session.start();
  });

  afterEach(() => {
    session.stop();
    document.body.querySelectorAll('.cdk-overlay-container').forEach((el) => el.remove());
    vi.useRealTimers();
  });

  const press = () => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab' }));
  const dialog = () => document.querySelector('[role=alertdialog]');
  /** The dialog's code is imported on demand: wait until it has loaded and rendered. */
  const warningShown = () =>
    vi.waitFor(() => {
      TestBed.inject(ApplicationRef).tick();
      expect(dialog()?.textContent).toContain("You'll be signed out soon");
    });
  const button = (label: string) =>
    Array.from(document.querySelectorAll<HTMLButtonElement>('nb-session-warning button')).find(
      (b) => b.textContent?.includes(label),
    )!;

  it('warns after 25 minutes without activity, as an alert dialog', async () => {
    await vi.advanceTimersByTimeAsync(25 * MINUTE - 1000);
    expect(dialog()).toBeNull();

    await vi.advanceTimersByTimeAsync(1000);
    await warningShown();
    expect(dialog()!.getAttribute('aria-labelledby')).toBe('session-warning-title');
    expect(dialog()!.getAttribute('aria-modal')).toBe('true');
    expect(dialog()!.textContent).toMatch(/[45]:\d\d left/);
  });

  it('activity postpones the warning', async () => {
    await vi.advanceTimersByTimeAsync(20 * MINUTE);
    press();
    await vi.advanceTimersByTimeAsync(20 * MINUTE);
    expect(dialog()).toBeNull();
    await vi.advanceTimersByTimeAsync(5 * MINUTE);
    await warningShown();
  });

  it('signs out at 30 minutes, saying why', async () => {
    await vi.advanceTimersByTimeAsync(30 * MINUTE);
    expect(auth.logout).toHaveBeenCalledWith('idle');
  });

  it('"Stay signed in" renews the sign-in and starts the clock again', async () => {
    await vi.advanceTimersByTimeAsync(25 * MINUTE);
    await warningShown();
    button('Stay signed in').click();
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    refreshes[0].next('token');
    refreshes[0].complete();
    expect(dialog()).toBeNull();

    await vi.advanceTimersByTimeAsync(29 * MINUTE);
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('"Sign out" signs out at once', async () => {
    await vi.advanceTimersByTimeAsync(25 * MINUTE);
    await warningShown();
    button('Sign out').click();
    expect(auth.logout).toHaveBeenCalledWith();
  });

  it('renews on activity once the sign-in is over 10 minutes old, not before', async () => {
    await vi.advanceTimersByTimeAsync(5 * MINUTE);
    press();
    expect(auth.refresh).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(6 * MINUTE);
    press();
    expect(auth.refresh).toHaveBeenCalledTimes(1);
  });

  it('a refused renewal ends the session; a network error does not', async () => {
    await vi.advanceTimersByTimeAsync(11 * MINUTE);
    press();
    refreshes[0].error(new HttpErrorResponse({ status: 0 }));
    expect(auth.endSession).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(2000);
    press();
    refreshes[1].error(new HttpErrorResponse({ status: 401 }));
    expect(auth.endSession).toHaveBeenCalledWith('expired');
  });
});
