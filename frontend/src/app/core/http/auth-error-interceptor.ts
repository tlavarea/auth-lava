import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthApi } from '@core/auth/auth-api';
import { AuthStore, AuthStoreType } from '@core/auth/auth.store';

const REFRESH_EXEMPT_PATHS: string[] = [
  '/api/auth/login',
  '/api/auth/register/start',
  '/api/auth/register/verify-code',
  '/api/auth/register/complete',
  '/api/auth/refresh',
  '/api/auth/me',
];

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const authApi: AuthApi = inject(AuthApi);
  const authStore: AuthStoreType = inject(AuthStore);
  const router: Router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      const isRetryableUnauthorized: boolean =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !REFRESH_EXEMPT_PATHS.some((path) => req.url.includes(path));

      if (!isRetryableUnauthorized) {
        return throwError(() => error);
      }

      return authApi.refresh().pipe(
        switchMap(() => next(req)),
        catchError((refreshError: unknown) => {
          authStore.forceLogout();
          router.navigateByUrl('/login');
          return throwError(() => refreshError);
        })
      );
    })
  );
};
