import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { AuthStore } from '@core/auth/auth.store';
import { authErrorInterceptor } from '@core/http/auth-error-interceptor';
import { credentialsInterceptor } from '@core/http/credentials-interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([credentialsInterceptor, authErrorInterceptor])),
    provideAppInitializer(() => inject(AuthStore).bootstrap()),
  ],
};
