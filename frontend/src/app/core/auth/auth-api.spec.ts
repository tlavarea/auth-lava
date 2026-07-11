import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthApi } from './auth-api';

describe('AuthApi', () => {
  let service: AuthApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthApi);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
