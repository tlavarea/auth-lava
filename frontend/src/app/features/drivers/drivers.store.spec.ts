import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DriverDetailResponse, DriverListingRow } from './drivers.models';
import { DriversStore } from './drivers.store';

describe('DriversStore', () => {
  let store: InstanceType<typeof DriversStore>;
  let httpMock: HttpTestingController;

  const listing: DriverListingRow = {
    id: 'driver-42',
    name: 'Jane Doe',
    activationStatus: 'active',
    currentVehicleName: 'Truck 7',
    dutyStatus: 'driving',
    currentLocation: 'Fayetteville, NC',
  };

  const detail: DriverDetailResponse = {
    id: 'driver-42',
    name: 'Jane Doe',
    username: 'jdoe',
    email: 'jane@example.com',
    phone: '555-0100',
    licenseNumber: 'D1234567',
    licenseState: 'NC',
    activationStatus: 'active',
    dutyStatus: 'driving',
    tags: 'east-coast,ftl',
    currentVehicleId: 'vehicle-7',
    currentVehicleName: 'Truck 7',
    latitude: 35.0527,
    longitude: -78.8784,
    heading: 90,
    speed: 55,
    locationTime: '2026-07-14T00:00:00',
    formattedLocation: 'Fayetteville, NC',
    rawResponse: '{}',
    syncedAt: '2026-07-14T00:00:00',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), DriversStore],
    });
    store = TestBed.inject(DriversStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts empty and idle', () => {
    expect(store.drivers()).toEqual([]);
    expect(store.listStatus()).toBe('idle');
    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });

  it('loadDrivers() populates the list on success', async () => {
    const loadPromise = store.loadDrivers();
    httpMock.expectOne('/api/sw-expedited/drivers').flush([listing]);
    await loadPromise;

    expect(store.drivers()).toEqual([listing]);
    expect(store.listStatus()).toBe('idle');
  });

  it('loadDrivers() marks the list status as error on failure', async () => {
    const loadPromise = store.loadDrivers();
    httpMock.expectOne('/api/sw-expedited/drivers').flush(null, { status: 500, statusText: 'Server Error' });
    await loadPromise;

    expect(store.listStatus()).toBe('error');
  });

  it('loadDriverDetail() populates the selected detail on success', async () => {
    const loadPromise = store.loadDriverDetail('driver-42');
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    await loadPromise;

    expect(store.selectedDetail()).toEqual(detail);
    expect(store.detailStatus()).toBe('idle');
  });

  it('loadDriverDetail() marks the detail status as error on failure', async () => {
    const loadPromise = store.loadDriverDetail('driver-42');
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(null, { status: 404, statusText: 'Not Found' });
    await loadPromise;

    expect(store.detailStatus()).toBe('error');
  });

  it('refreshDriverDetail() replaces the selected detail without touching detailStatus', async () => {
    const loadPromise = store.loadDriverDetail('driver-42');
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    await loadPromise;

    const refreshedDetail = { ...detail, latitude: 36.0, longitude: -79.0 };
    const refreshPromise = store.refreshDriverDetail('driver-42');
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(refreshedDetail);
    await refreshPromise;

    expect(store.selectedDetail()).toEqual(refreshedDetail);
    expect(store.detailStatus()).toBe('idle');
  });

  it('refreshDriverDetail() silently keeps the existing detail on failure', async () => {
    const loadPromise = store.loadDriverDetail('driver-42');
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    await loadPromise;

    const refreshPromise = store.refreshDriverDetail('driver-42');
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(null, { status: 500, statusText: 'Server Error' });
    await refreshPromise;

    expect(store.selectedDetail()).toEqual(detail);
    expect(store.detailStatus()).toBe('idle');
  });

  it('clearSelectedDetail() resets the selected detail', async () => {
    const loadPromise = store.loadDriverDetail('driver-42');
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    await loadPromise;

    store.clearSelectedDetail();

    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });
});
