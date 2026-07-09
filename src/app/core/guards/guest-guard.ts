import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';

export const guestGuard: CanActivateFn = (): boolean | UrlTree => {
  const authStore: AuthStoreType = inject(AuthStore);
  const router = inject(Router);

  switch (authStore.status()) {
    case 'authenticated':
      return router.parseUrl('/');
    case 'mfa-pending':
      return router.parseUrl('/mfa/verify');
    default:
      return true;
  }
};
