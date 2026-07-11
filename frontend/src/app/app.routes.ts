import { Routes } from '@angular/router';

import { authGuard } from '@core/guards/auth-guard';
import { guestGuard } from '@core/guards/guest-guard';
import { mfaPendingGuard } from '@core/guards/mfa-pending-guard';

export const routes: Routes = [
  {
    path: '',
    title: 'Dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
  },
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
    path: 'mfa/enroll',
    title: 'Set up two-factor authentication',
    canActivate: [authGuard],
    loadComponent: () => import('./features/mfa-enroll/mfa-enroll.page').then((m) => m.MfaEnrollPage),
  },
  {
    path: 'mfa/disable',
    title: 'Disable two-factor authentication',
    canActivate: [authGuard],
    loadComponent: () => import('./features/mfa-disable/mfa-disable.page').then((m) => m.MfaDisablePage),
  },
  {
    path: 'mfa/verify',
    title: 'Two-factor verification',
    canActivate: [mfaPendingGuard],
    loadComponent: () => import('./features/mfa-verify/mfa-verify.page').then((m) => m.MfaVerifyPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
