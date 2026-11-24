import { Injectable, signal } from '@angular/core';

const KEY = 'nb.hideBalances';

/**
 * "Hide balances", for using the app where others can see the screen. Remembered on this device
 * (only the yes/no; no amounts are ever stored).
 */
@Injectable({ providedIn: 'root' })
export class BalanceVisibility {
  private readonly hiddenState = signal(read());
  readonly hidden = this.hiddenState.asReadonly();

  toggle(): void {
    const hidden = !this.hiddenState();
    this.hiddenState.set(hidden);
    try {
      localStorage.setItem(KEY, hidden ? '1' : '0');
    } catch {
      // Storage blocked (private mode, policy): the choice lasts for this visit only.
    }
  }
}

function read(): boolean {
  try {
    return localStorage.getItem(KEY) === '1';
  } catch {
    return false;
  }
}
