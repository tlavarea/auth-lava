import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DriversApi } from './drivers-api';

describe('DriversApi', () => {
  let service: DriversApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DriversApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('list() GETs /api/sw-expedited/drivers', () => {
    const listPromise = service.list();
    listPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/drivers').flush([]);
  });

  it('detail() GETs /api/sw-expedited/drivers/:driverId', () => {
    const detailPromise = service.detail('driver-42');
    detailPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(null);
  });
});
