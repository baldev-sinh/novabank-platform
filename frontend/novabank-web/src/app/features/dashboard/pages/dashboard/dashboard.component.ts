import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CardComponent } from '../../../../shared/ui';

@Component({
  selector: 'nb-dashboard',
  imports: [CardComponent],
  // Placeholder until the accounts overview (roadmap 8.4).
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {}
