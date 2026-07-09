import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthApi } from '../auth/auth-api';
import { AuthStore } from '../auth/auth.store';

const REFRESH_EXEMPT_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/me'];

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const authApi = inject(AuthApi);
  const authStore = inject(AuthStore);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      const isRetryableUnauthorized =
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
