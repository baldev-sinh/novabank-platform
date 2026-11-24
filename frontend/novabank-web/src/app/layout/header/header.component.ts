import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
import { CustomerService } from '../../features/customer/customer.service';
import { LogoComponent } from '../logo/logo.component';

/** The signed-in header: the main sections, who is signed in, and sign out. */
@Component({
  selector: 'nb-header',
  imports: [RouterLink, RouterLinkActive, LogoComponent],
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
  /** Banking sections only once the identity check is approved. */
  protected readonly showNav = computed(() => this.customers.profile()?.status === 'ACTIVE');

  protected signOut(): void {
    this.authenticationService.logout();
  }
}
