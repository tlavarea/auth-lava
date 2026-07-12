import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  ErrorHandler,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';

import { AuthStore } from '@core/auth/auth.store';
import { GlobalErrorHandler } from '@core/error-handling/global-error-handler';
import { authErrorInterceptor } from '@core/http/auth-error-interceptor';
import { credentialsInterceptor } from '@core/http/credentials-interceptor';
import { ThemeStore } from '@core/theme/theme.store';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    provideRouter(routes),
    provideHttpClient(withInterceptors([credentialsInterceptor, authErrorInterceptor])),
    provideAppInitializer(() => inject(AuthStore).bootstrap()),
    provideAppInitializer(() => inject(ThemeStore).initialize()),
  ],
};
