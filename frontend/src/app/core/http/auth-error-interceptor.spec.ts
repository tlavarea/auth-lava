import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
  HttpResponse,
  provideHttpClient,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom, of, throwError } from 'rxjs';

import { authErrorInterceptor } from './auth-error-interceptor';

describe('authErrorInterceptor', () => {
  const interceptor: HttpInterceptorFn = (req, next) =>
    TestBed.runInInjectionContext(() => authErrorInterceptor(req, next));

  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('passes through successful responses unchanged', async () => {
    const req = new HttpRequest('GET', '/api/auth/dashboard');
    const okResponse = new HttpResponse({ status: 200, body: 'ok' });
    const result = await firstValueFrom(interceptor(req, () => of(okResponse)));
    expect(result).toBe(okResponse);
  });

  it('refreshes and retries once on a 401 from a non-exempt endpoint', async () => {
    const req = new HttpRequest('GET', '/api/auth/dashboard');
    const unauthorized = new HttpErrorResponse({ status: 401 });
    const retriedResponse = new HttpResponse({ status: 200, body: 'retried' });
    let attempt = 0;

    const result$ = interceptor(req, () => {
      attempt += 1;
      return attempt === 1 ? throwError(() => unauthorized) : of(retriedResponse);
    });
    const resultPromise = firstValueFrom(result$);

    httpMock.expectOne('/api/auth/refresh').flush(null);

    expect(await resultPromise).toBe(retriedResponse);
    expect(attempt).toBe(2);
  });

  it('does not retry a 401 from /api/auth/me', async () => {
    const req = new HttpRequest('GET', '/api/auth/me');
    const unauthorized = new HttpErrorResponse({ status: 401 });

    await expect(firstValueFrom(interceptor(req, () => throwError(() => unauthorized)))).rejects.toBe(unauthorized);
  });

  it('does not retry a 401 from /api/auth/mfa/verify', async () => {
    const req = new HttpRequest('POST', '/api/auth/mfa/verify', { code: '000000' });
    const unauthorized = new HttpErrorResponse({ status: 401 });

    await expect(firstValueFrom(interceptor(req, () => throwError(() => unauthorized)))).rejects.toBe(unauthorized);
  });

  it('force-logs-out and redirects to /login when refresh fails', async () => {
    const req = new HttpRequest('GET', '/api/auth/dashboard');
    const unauthorized = new HttpErrorResponse({ status: 401 });
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const result$ = interceptor(req, () => throwError(() => unauthorized));
    const resultPromise = firstValueFrom(result$).catch((error: unknown) => error);

    httpMock.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    await resultPromise;
    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });
});
