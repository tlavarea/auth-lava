import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';

export const mfaPendingGuard: CanActivateFn = (): boolean | UrlTree => {
  const authStore: AuthStoreType = inject(AuthStore);
  const router: Router = inject(Router);

  switch (authStore.status()) {
    case 'mfa-pending':
      return true;
    case 'authenticated':
      return router.parseUrl('/');
    default:
      return router.parseUrl('/login');
  }
};
