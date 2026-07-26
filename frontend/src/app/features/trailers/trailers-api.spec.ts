import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TrailersApi } from './trailers-api';

describe('TrailersApi', () => {
  let service: TrailersApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TrailersApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('list() GETs /api/sw-expedited/trailers', () => {
    const listPromise = service.list();
    listPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/trailers').flush([]);
  });

  it('detail() GETs /api/sw-expedited/trailers/:trailerId', () => {
    const detailPromise = service.detail('trailer-1');
    detailPromise.subscribe();

    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(null);
  });
});
