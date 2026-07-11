import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UserResponse } from './auth.models';
import { AuthStore } from './auth.store';

describe('AuthStore', () => {
  let store: InstanceType<typeof AuthStore>;
  let httpMock: HttpTestingController;

  const user: UserResponse = {
    id: 1,
    email: 'user@example.com',
    emailVerified: true,
    authorities: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(AuthStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts in the unknown status', () => {
    expect(store.status()).toBe('unknown');
    expect(store.user()).toBeNull();
  });

  it('bootstrap() marks the store authenticated on a successful /me', async () => {
    const bootstrapPromise = store.bootstrap();
    httpMock.expectOne('/api/auth/me').flush(user);
    await bootstrapPromise;

    expect(store.status()).toBe('authenticated');
    expect(store.user()).toEqual(user);
  });

  it('bootstrap() marks the store mfa-pending on a 403 from /me', async () => {
    const bootstrapPromise = store.bootstrap();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 403, statusText: 'Forbidden' });
    await bootstrapPromise;

    expect(store.status()).toBe('mfa-pending');
    expect(store.user()).toBeNull();
  });

  it('bootstrap() marks the store anonymous on a 401 from /me', async () => {
    const bootstrapPromise = store.bootstrap();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await bootstrapPromise;

    expect(store.status()).toBe('anonymous');
    expect(store.user()).toBeNull();
  });

  it('login() chains a /me call and reports mfa-pending on a 403', async () => {
    const loginPromise = store.login({ email: user.email, password: 'password123' });
    httpMock.expectOne('/api/auth/login').flush(user);
    await Promise.resolve();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 403, statusText: 'Forbidden' });
    await loginPromise;

    expect(store.status()).toBe('mfa-pending');
  });

  it('logout() clears local state even when the backend returns 403', async () => {
    const logoutPromise = store.logout();
    httpMock.expectOne('/api/auth/logout').flush(null, { status: 403, statusText: 'Forbidden' });
    await logoutPromise;

    expect(store.status()).toBe('anonymous');
    expect(store.user()).toBeNull();
  });
});
