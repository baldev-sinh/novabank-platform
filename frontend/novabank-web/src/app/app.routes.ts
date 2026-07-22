import { Routes } from '@angular/router';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { LoginComponent } from './features/auth/pages/login/login.component';
import { authGuard } from './core/guards/auth.guard';
import { DashboardComponent } from './features/dashboard/pages/dashboard/dashboard.component';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent,
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        component: DashboardComponent,
      },
    ],
  },
];
