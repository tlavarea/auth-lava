import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CanActivateFn, provideRouter, Router } from '@angular/router';

import { AuthStore } from '../auth/auth.store';
import { mfaPendingGuard } from './mfa-pending-guard';

describe('mfaPendingGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => mfaPendingGuard(...guardParameters));

  let authStore: InstanceType<typeof AuthStore>;
  let router: Router;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    authStore = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('allows activation when mfa-pending', async () => {
    const bootstrapPromise = authStore.bootstrap();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 403, statusText: 'Forbidden' });
    await bootstrapPromise;

    expect(executeGuard(undefined as never, undefined as never)).toBe(true);
  });

  it('redirects to / when authenticated', async () => {
    const bootstrapPromise = authStore.bootstrap();
    httpMock.expectOne('/api/auth/me').flush({ id: 1, email: 'a@b.com', emailVerified: true, authorities: [] });
    await bootstrapPromise;

    expect(executeGuard(undefined as never, undefined as never)).toEqual(router.parseUrl('/'));
  });

  it('redirects to /login when anonymous', async () => {
    const bootstrapPromise = authStore.bootstrap();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await bootstrapPromise;

    expect(executeGuard(undefined as never, undefined as never)).toEqual(router.parseUrl('/login'));
  });
});
