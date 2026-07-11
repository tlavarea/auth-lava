import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';

export const authGuard: CanActivateFn = (): boolean | UrlTree => {
  const authStore: AuthStoreType = inject(AuthStore);
  const router: Router = inject(Router);

  switch (authStore.status()) {
    case 'authenticated':
      return true;
    case 'mfa-pending':
      return router.parseUrl('/mfa/verify');
    default:
      return router.parseUrl('/login');
  }
};
