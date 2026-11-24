import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthenticationService } from '../../features/auth/services/authentication.service';
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

  protected readonly user = this.authenticationService.currentUser;

  protected signOut(): void {
    this.authenticationService.logout();
  }
}
