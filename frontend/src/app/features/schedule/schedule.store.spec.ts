import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { startOfDayMs, WEEK_MS } from './schedule-chart';
import { DriverScheduleRow, ManifestEta, ManifestRoute } from './schedule.models';
import { ScheduleStore } from './schedule.store';

describe('ScheduleStore', () => {
  let store: InstanceType<typeof ScheduleStore>;
  let httpMock: HttpTestingController;

  const row: DriverScheduleRow = {
    driverId: 'driver-42',
    driverName: 'Jane Doe',
    activationStatus: 'active',
    dutyStatus: 'driving',
    manifests: [
      {
        manifestNumber: 1000589,
        manifestStatus: 'manifest_in_progress',
        pickupAppointmentStart: '2026-07-17T08:00:00',
        eta: '2026-07-20T10:00:00',
        origin: '4251 Turin Dr, Bessemer, AL 35020',
        destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
        loadReference: 'SwX-1000589',
      },
    ],
  };

  const route: ManifestRoute = {
    stops: [],
    startingPosition: null,
    encodedPolyline: 'abc123',
    distanceMeters: 1_800_000,
    duration: '64800s',
  };

  const eta: ManifestEta = {
    stopSequenceNumber: 1,
    remainingMiles: 553,
    remainingMinutes: 540,
    estimatedArrival: '2026-07-19T02:16:00',
  };

  function flushManifestRoute(): void {
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/manifests/1000589/route').flush(route);
  }

  function flushManifestEta(): void {
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/manifests/1000589/eta').flush(eta);
  }

  beforeEach(() => {
    vi.setSystemTime(new Date(2026, 6, 17, 12, 0, 0));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ScheduleStore],
    });
    store = TestBed.inject(ScheduleStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('starts empty and idle, with weekStartMs at the start of today', () => {
    expect(store.rows()).toEqual([]);
    expect(store.status()).toBe('idle');
    expect(store.weekStartMs()).toBe(startOfDayMs(Date.now()));
    expect(store.selectedDriverId()).toBeNull();
    expect(store.selectedManifest()).toBeNull();
    expect(store.selectedManifestRoute()).toBeNull();
    expect(store.selectedManifestEta()).toBeNull();
  });

  it('selectManifest() sets the selected driver id and manifest, then fetches its route and eta', async () => {
    const selectPromise = store.selectManifest('driver-42', row.manifests[0]);

    expect(store.selectedDriverId()).toBe('driver-42');
    expect(store.selectedManifest()).toEqual(row.manifests[0]);
    expect(store.selectedManifestRoute()).toBeNull();
    expect(store.selectedManifestEta()).toBeNull();

    flushManifestRoute();
    flushManifestEta();
    await selectPromise;

    expect(store.selectedManifestRoute()).toEqual(route);
    expect(store.selectedManifestEta()).toEqual(eta);
  });

  it('clearSelection() resets the selected driver id, manifest, route, and eta', async () => {
    const selectPromise = store.selectManifest('driver-42', row.manifests[0]);
    flushManifestRoute();
    flushManifestEta();
    await selectPromise;

    store.clearSelection();

    expect(store.selectedDriverId()).toBeNull();
    expect(store.selectedManifest()).toBeNull();
    expect(store.selectedManifestRoute()).toBeNull();
    expect(store.selectedManifestEta()).toBeNull();
  });

  it('loadSchedule() populates rows on success', async () => {
    const loadPromise = store.loadSchedule();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await loadPromise;

    expect(store.rows()).toEqual([row]);
    expect(store.status()).toBe('idle');
  });

  it('loadSchedule() marks status as error on failure', async () => {
    const loadPromise = store.loadSchedule();
    httpMock
      .expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await loadPromise;

    expect(store.status()).toBe('error');
  });

  it('refreshSchedule() replaces rows without touching status', async () => {
    const loadPromise = store.loadSchedule();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await loadPromise;

    const updatedRow = { ...row, dutyStatus: 'onDuty' };
    const refreshPromise = store.refreshSchedule();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([updatedRow]);
    await refreshPromise;

    expect(store.rows()).toEqual([updatedRow]);
    expect(store.status()).toBe('idle');
  });

  it('refreshSchedule() silently keeps the existing rows on failure', async () => {
    const loadPromise = store.loadSchedule();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await loadPromise;

    const refreshPromise = store.refreshSchedule();
    httpMock
      .expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await refreshPromise;

    expect(store.rows()).toEqual([row]);
    expect(store.status()).toBe('idle');
  });

  it('goToPreviousWeek() moves weekStartMs back a week and reloads', async () => {
    const initialWeekStart = store.weekStartMs();

    const goPromise = store.goToPreviousWeek();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await goPromise;

    expect(store.weekStartMs()).toBe(initialWeekStart - WEEK_MS);
    expect(store.rows()).toEqual([row]);
  });

  it('goToNextWeek() moves weekStartMs forward a week and reloads', async () => {
    const initialWeekStart = store.weekStartMs();

    const goPromise = store.goToNextWeek();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await goPromise;

    expect(store.weekStartMs()).toBe(initialWeekStart + WEEK_MS);
  });

  it('goToCurrentWeek() resets weekStartMs to the start of today and reloads', async () => {
    const previousPromise = store.goToPreviousWeek();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await previousPromise;

    const currentPromise = store.goToCurrentWeek();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await currentPromise;

    expect(store.weekStartMs()).toBe(startOfDayMs(Date.now()));
  });

  it('goToPreviousWeek() clears an existing manifest selection', async () => {
    const selectPromise = store.selectManifest('driver-42', row.manifests[0]);
    flushManifestRoute();
    flushManifestEta();
    await selectPromise;

    const goPromise = store.goToPreviousWeek();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await goPromise;

    expect(store.selectedDriverId()).toBeNull();
    expect(store.selectedManifest()).toBeNull();
    expect(store.selectedManifestRoute()).toBeNull();
    expect(store.selectedManifestEta()).toBeNull();
  });

  it('goToNextWeek() clears an existing manifest selection', async () => {
    const selectPromise = store.selectManifest('driver-42', row.manifests[0]);
    flushManifestRoute();
    flushManifestEta();
    await selectPromise;

    const goPromise = store.goToNextWeek();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await goPromise;

    expect(store.selectedDriverId()).toBeNull();
    expect(store.selectedManifest()).toBeNull();
    expect(store.selectedManifestRoute()).toBeNull();
    expect(store.selectedManifestEta()).toBeNull();
  });

  it('goToCurrentWeek() clears an existing manifest selection', async () => {
    const selectPromise = store.selectManifest('driver-42', row.manifests[0]);
    flushManifestRoute();
    flushManifestEta();
    await selectPromise;

    const goPromise = store.goToCurrentWeek();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await goPromise;

    expect(store.selectedDriverId()).toBeNull();
    expect(store.selectedManifest()).toBeNull();
    expect(store.selectedManifestRoute()).toBeNull();
    expect(store.selectedManifestEta()).toBeNull();
  });
});
