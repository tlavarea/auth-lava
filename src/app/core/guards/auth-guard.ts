import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '../auth/auth.store';

export const authGuard: CanActivateFn = () => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  switch (authStore.status()) {
    case 'authenticated':
      return true;
    case 'mfa-pending':
      return router.parseUrl('/mfa/verify');
    default:
      return router.parseUrl('/login');
  }
};
