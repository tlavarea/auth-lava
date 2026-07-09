import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '../auth/auth.store';

export const mfaPendingGuard: CanActivateFn = () => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  switch (authStore.status()) {
    case 'mfa-pending':
      return true;
    case 'authenticated':
      return router.parseUrl('/');
    default:
      return router.parseUrl('/login');
  }
};
