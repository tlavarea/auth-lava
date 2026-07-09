import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { credentialsInterceptor } from './credentials-interceptor';

describe('credentialsInterceptor', () => {
  const interceptor: HttpInterceptorFn = (req, next) =>
    TestBed.runInInjectionContext(() => credentialsInterceptor(req, next));

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('clones the request with withCredentials set to true', () => {
    const req = new HttpRequest('GET', '/api/auth/me');
    expect(req.withCredentials).toBe(false);

    interceptor(req, (clonedReq) => {
      expect(clonedReq.withCredentials).toBe(true);
      return of();
    });
  });
});
