import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
import { CustomerService } from '../../features/customer/customer.service';
import { LogoComponent } from '../logo/logo.component';

/** The signed-in header: the main sections, who is signed in, and sign out. */
@Component({
  selector: 'nb-header',
  imports: [RouterLink, LogoComponent],
  templateUrl: './header.component.html',
  styleUrl: './header.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HeaderComponent {
  private readonly authenticationService = inject(AuthenticationService);

  private readonly customers = inject(CustomerService);

  /** The customer's name once they have a profile, else the sign-in email. */
  protected readonly displayName = computed(() => {
    const profile = this.customers.profile();
    return profile
      ? `${profile.firstName} ${profile.lastName}`
      : (this.authenticationService.currentUser()?.email ?? '');
  });
  private readonly router = inject(Router);
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );
  /**
   * "Accounts" covers the overview and everything under /accounts. routerLinkActive can't say that:
   * exact matching misses /accounts/…, and a prefix match on "/" would match every page.
   */
  private readonly path = computed(() => this.url().split(/[?#]/)[0]);
  protected readonly accountsActive = computed(
    () => this.path() === '/' || this.path().startsWith('/accounts'),
  );
  protected readonly transferActive = computed(() => this.path().startsWith('/transfer'));

  /** Banking sections only once the identity check is approved. */
  protected readonly showNav = computed(() => this.customers.profile()?.status === 'ACTIVE');

  protected signOut(): void {
    this.authenticationService.logout();
  }
}
