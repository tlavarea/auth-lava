import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { startOfDayMs, WEEK_MS } from './schedule-chart';
import { DriverScheduleRow } from './schedule.models';
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
        manifestStatus: 'manifest_in_progress',
        pickupAppointmentStart: '2026-07-17T08:00:00',
        eta: '2026-07-20T10:00:00',
        origin: '4251 Turin Dr, Bessemer, AL 35020',
        destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
        loadReference: 'SwX-1000589',
      },
    ],
  };

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
});
