import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TrucksApi } from './trucks-api';

describe('TrucksApi', () => {
  let service: TrucksApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TrucksApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('list() GETs /api/sw-expedited/trucks', () => {
    const listPromise = service.list();
    listPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/trucks').flush([]);
  });

  it('detail() GETs /api/sw-expedited/trucks/:truckId', () => {
    const detailPromise = service.detail('truck-1');
    detailPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(null);
  });
});
