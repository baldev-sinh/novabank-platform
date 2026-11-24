import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';

/** Around the signed-in pages. */
@Component({
  selector: 'nb-main-layout',
  imports: [HeaderComponent, FooterComponent, RouterOutlet],
  template: `
    <nb-header />
    <main id="main" class="main">
      <router-outlet />
    </main>
    <nb-footer />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }
    .main {
      flex: 1;
      width: 100%;
      max-width: var(--nb-content-max);
      margin: 0 auto;
      padding: var(--nb-space-10) var(--nb-space-8) var(--nb-space-16);
    }
    @media (max-width: 40rem) {
      .main {
        padding: var(--nb-space-6) var(--nb-space-4) var(--nb-space-12);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainLayoutComponent {}
