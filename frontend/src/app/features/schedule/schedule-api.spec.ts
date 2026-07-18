import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ScheduleApi } from './schedule-api';

describe('ScheduleApi', () => {
  let service: ScheduleApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ScheduleApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() GETs /api/sw-expedited/drivers/timeline with weekStart as an ISO query param', () => {
    const weekStartMs = new Date(2026, 6, 17, 0, 0, 0).getTime();

    service.list(weekStartMs).subscribe();

    const request = httpMock.expectOne(
      (req) => req.url === '/api/sw-expedited/drivers/timeline' && req.params.get('weekStart') !== null
    );
    expect(request.request.params.get('weekStart')).toBe(new Date(weekStartMs).toISOString());
    request.flush([]);
  });

  it('route() GETs /api/sw-expedited/manifests/:manifestNumber/route', () => {
    service.route(1000589).subscribe();

    httpMock.expectOne('/api/sw-expedited/manifests/1000589/route').flush(null);
  });
});
