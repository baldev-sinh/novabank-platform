import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Any address the back-office doesn't have. */
@Component({
  selector: 'nb-not-found',
  imports: [RouterLink],
  template: `
    <h1 class="title">Page not found</h1>
    <p class="text">
      This address isn't part of the back-office. Check it, or
      <a routerLink="/sign-in">go to sign in</a>.
    </p>
  `,
  styles: `
    .title {
      margin: 0 0 var(--nb-space-3);
      font-size: var(--nb-font-size-2xl);
      line-height: var(--nb-line-height-tight);
      font-weight: var(--nb-font-weight-bold);
    }
    .text {
      margin: 0;
      color: var(--nb-color-ink-soft);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundComponent {}
