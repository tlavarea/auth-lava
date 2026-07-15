import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ShipmentsApi } from './shipments-api';

describe('ShipmentsApi', () => {
  let service: ShipmentsApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ShipmentsApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('list() GETs /api/sw-expedited/shipments', () => {
    const listPromise = service.list();
    listPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/shipments').flush([]);
  });

  it('detail() GETs /api/sw-expedited/shipments/:offerId', () => {
    const detailPromise = service.detail(42);
    detailPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(null);
  });
});
