import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TruckDetailResponse, TruckListingRow } from './trucks.models';
import { TrucksStore } from './trucks.store';

describe('TrucksStore', () => {
  let store: InstanceType<typeof TrucksStore>;
  let httpMock: HttpTestingController;

  const listing: TruckListingRow = {
    id: 'truck-1',
    truckNumber: 'T1000',
    statusCode: 1,
    currentDriverName: 'Jane Trucker',
    currentTrailerLabel: "T231 - 53' SDL",
  };

  const detail: TruckDetailResponse = {
    id: 'truck-1',
    truckNumber: 'T1000',
    statusCode: 1,
    vin: '1FUJA6CV12LM12345',
    make: 'Freightliner',
    model: 'Cascadia',
    year: 2023,
    currentDriverName: 'Jane Trucker',
    currentTrailerLabel: "T231 - 53' SDL",
    syncedAt: '2026-07-14T00:00:00',
    fuelPercent: null,
    odometerMiles: null,
    engineHours: null,
    faultCodes: null,
    engineState: null,
    defLevelPercent: null,
    batteryVolts: null,
    coolantTempF: null,
    engineRpm: null,
    engineLoadPercent: null,
    latitude: null,
    longitude: null,
    formattedLocation: null,
    locationTime: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TrucksStore],
    });
    store = TestBed.inject(TrucksStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts empty and idle', () => {
    expect(store.trucks()).toEqual([]);
    expect(store.listStatus()).toBe('idle');
    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });

  it('loadTrucks() populates the list on success', async () => {
    const loadPromise = store.loadTrucks();
    httpMock.expectOne('/api/sw-expedited/trucks').flush([listing]);
    await loadPromise;

    expect(store.trucks()).toEqual([listing]);
    expect(store.listStatus()).toBe('idle');
  });

  it('loadTrucks() marks the list status as error on failure', async () => {
    const loadPromise = store.loadTrucks();
    httpMock.expectOne('/api/sw-expedited/trucks').flush(null, { status: 500, statusText: 'Server Error' });
    await loadPromise;

    expect(store.listStatus()).toBe('error');
  });

  it('loadTruckDetail() populates the selected detail on success', async () => {
    const loadPromise = store.loadTruckDetail('truck-1');
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await loadPromise;

    expect(store.selectedDetail()).toEqual(detail);
    expect(store.detailStatus()).toBe('idle');
  });

  it('loadTruckDetail() marks the detail status as error on failure', async () => {
    const loadPromise = store.loadTruckDetail('truck-1');
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(null, { status: 404, statusText: 'Not Found' });
    await loadPromise;

    expect(store.detailStatus()).toBe('error');
  });

  it('refreshTruckDetail() replaces the selected detail without touching detailStatus', async () => {
    const loadPromise = store.loadTruckDetail('truck-1');
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await loadPromise;

    const refreshedDetail: TruckDetailResponse = { ...detail, fuelPercent: 61 };
    const refreshPromise = store.refreshTruckDetail('truck-1');
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(refreshedDetail);
    await refreshPromise;

    expect(store.selectedDetail()).toEqual(refreshedDetail);
    expect(store.detailStatus()).toBe('idle');
  });

  it('clearSelectedDetail() resets the selected detail', async () => {
    const loadPromise = store.loadTruckDetail('truck-1');
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await loadPromise;

    store.clearSelectedDetail();

    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });
});
