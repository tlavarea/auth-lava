import { Routes } from '@angular/router';

import { authGuard } from '@core/guards/auth-guard';
import { guestGuard } from '@core/guards/guest-guard';
import { mfaPendingGuard } from '@core/guards/mfa-pending-guard';

export const routes: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/layout/app-shell').then((m) => m.AppShell),
    children: [
      {
        path: '',
        title: 'Dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'profile',
        title: 'Profile',
        loadComponent: () => import('./features/profile/profile.page').then((m) => m.ProfilePage),
      },
      {
        path: 'shipments',
        loadChildren: () => import('./features/shipments/shipments.routes').then((m) => m.shipmentsRoutes),
      },
    ],
  },
  {
    path: '',
    loadComponent: () => import('./shared/layout/guest-shell').then((m) => m.GuestShell),
    children: [
      {
        path: 'login',
        title: 'Sign in',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
      },
      {
        path: 'register',
        title: 'Create account',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/register/register.page').then((m) => m.RegisterPage),
      },
      {
        path: 'mfa/verify',
        title: 'Two-factor verification',
        canActivate: [mfaPendingGuard],
        loadComponent: () => import('./features/mfa-verify/mfa-verify.page').then((m) => m.MfaVerifyPage),
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
