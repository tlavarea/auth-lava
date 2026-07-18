import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DriverTimelineRow } from './timeline.models';
import { TimelineStore } from './timeline.store';

describe('TimelineStore', () => {
  let store: InstanceType<typeof TimelineStore>;
  let httpMock: HttpTestingController;

  const row: DriverTimelineRow = {
    driverId: 'driver-42',
    driverName: 'Jane Doe',
    activationStatus: 'active',
    dutyStatus: 'driving',
    manifestStatus: 'manifest_in_progress',
    pickupAppointmentStart: '2026-07-17T08:00:00',
    eta: '2026-07-20T10:00:00',
    destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
    loadReference: 'SwX-1000589',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TimelineStore],
    });
    store = TestBed.inject(TimelineStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts empty and idle', () => {
    expect(store.rows()).toEqual([]);
    expect(store.status()).toBe('idle');
  });

  it('loadTimeline() populates rows on success', async () => {
    const loadPromise = store.loadTimeline();
    httpMock.expectOne('/api/sw-expedited/drivers/timeline').flush([row]);
    await loadPromise;

    expect(store.rows()).toEqual([row]);
    expect(store.status()).toBe('idle');
  });

  it('loadTimeline() marks status as error on failure', async () => {
    const loadPromise = store.loadTimeline();
    httpMock.expectOne('/api/sw-expedited/drivers/timeline').flush(null, { status: 500, statusText: 'Server Error' });
    await loadPromise;

    expect(store.status()).toBe('error');
  });

  it('refreshTimeline() replaces rows without touching status', async () => {
    const loadPromise = store.loadTimeline();
    httpMock.expectOne('/api/sw-expedited/drivers/timeline').flush([row]);
    await loadPromise;

    const updatedRow = { ...row, dutyStatus: 'onDuty' };
    const refreshPromise = store.refreshTimeline();
    httpMock.expectOne('/api/sw-expedited/drivers/timeline').flush([updatedRow]);
    await refreshPromise;

    expect(store.rows()).toEqual([updatedRow]);
    expect(store.status()).toBe('idle');
  });

  it('refreshTimeline() silently keeps the existing rows on failure', async () => {
    const loadPromise = store.loadTimeline();
    httpMock.expectOne('/api/sw-expedited/drivers/timeline').flush([row]);
    await loadPromise;

    const refreshPromise = store.refreshTimeline();
    httpMock.expectOne('/api/sw-expedited/drivers/timeline').flush(null, { status: 500, statusText: 'Server Error' });
    await refreshPromise;

    expect(store.rows()).toEqual([row]);
    expect(store.status()).toBe('idle');
  });
});
