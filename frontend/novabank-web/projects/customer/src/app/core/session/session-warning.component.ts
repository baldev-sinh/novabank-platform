import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { ButtonComponent } from '@novabank/ui';

export type SessionWarningChoice = 'stay' | 'sign-out';

/**
 * "You'll be signed out soon": an alertdialog (CDK) that traps focus and doesn't close on Escape,
 * because the customer has to choose. The countdown is announced politely, once a minute.
 */
@Component({
  selector: 'nb-session-warning',
  imports: [ButtonComponent],
  template: `
    <div class="dialog">
      <h2 id="session-warning-title" class="title">You'll be signed out soon</h2>
      <p id="session-warning-text">
        For your security, we sign you out after 30 minutes without activity.
        <span class="nb-num">{{ remaining() }}</span> left.
      </p>
      <p class="nb-visually-hidden" aria-live="polite">{{ announcement() }}</p>
      <div class="actions">
        <button nbButton type="button" (click)="choose('stay')">Stay signed in</button>
        <button nbButton="neutral" type="button" (click)="choose('sign-out')">Sign out</button>
      </div>
    </div>
  `,
  styles: `
    .dialog {
      width: min(28rem, calc(100vw - 2rem));
      padding: var(--nb-space-7);
      background: var(--nb-color-surface);
      border-radius: var(--nb-radius-card);
      box-shadow: 0 12px 32px rgb(20 33 61 / 24%);
      display: flex;
      flex-direction: column;
      gap: var(--nb-space-4);
    }
    .title {
      font-size: var(--nb-font-size-xl);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--nb-space-3);
      margin-top: var(--nb-space-2);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionWarningComponent {
  private readonly dialogRef = inject<DialogRef<SessionWarningChoice>>(DialogRef);
  /** When the session ends, in ms since the epoch. */
  private readonly deadline = inject<{ deadline: number }>(DIALOG_DATA).deadline;

  private readonly now = signal(Date.now());

  protected readonly remaining = computed(() => {
    const seconds = Math.max(0, Math.ceil((this.deadline - this.now()) / 1000));
    return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  });

  /** Read out at the start and once a minute, not every second. */
  protected readonly announcement = computed(() => {
    const minutes = Math.ceil(Math.max(0, this.deadline - this.now()) / 60_000);
    return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} until you are signed out.`;
  });

  constructor() {
    const tick = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(tick));
  }

  protected choose(choice: SessionWarningChoice): void {
    this.dialogRef.close(choice);
  }
}
