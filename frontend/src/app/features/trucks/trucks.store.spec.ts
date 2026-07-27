import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  TruckDetailResponse,
  TruckListingRow,
  TruckRouteHistoryResponse,
  TruckSafetyEventEntry,
} from './trucks.models';
import { TrucksStore } from './trucks.store';

describe('TrucksStore', () => {
  let store: InstanceType<typeof TrucksStore>;
  let httpMock: HttpTestingController;

  const listing: TruckListingRow = {
    id: 'truck-1',
    truckNumber: 'T1000',
    engineState: 'On',
    ecuSpeedMph: null,
    currentDriverName: 'Jane Trucker',
    currentTrailerLabel: "T231 - 53' SDL",
  };

  const detail: TruckDetailResponse = {
    id: 'truck-1',
    truckNumber: 'T1000',
    statusCode: 1,
    vin: '1FUJA6CV12LM12345',
    licensePlate: '6YA522',
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
    ecuSpeedMph: null,
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

  const routeHistory: TruckRouteHistoryResponse = {
    points: [
      { time: '2026-07-27T12:00:00Z', latitude: 32.735, longitude: -97.108, headingDegrees: 180, speedMph: 62.3 },
    ],
    stops: [
      {
        latitude: 32.735,
        longitude: -97.108,
        formattedLocation: 'Fort Worth, TX',
        arrivalTime: '2026-07-27T12:05:00Z',
        departureTime: '2026-07-27T12:20:00Z',
        stoppedMinutes: 15,
      },
    ],
  };

  const safetyEvents: TruckSafetyEventEntry[] = [
    {
      id: 'evt-1',
      occurredAt: '2026-07-27T12:10:00Z',
      behaviorLabels: ['Harsh Brake'],
      latitude: 32.735,
      longitude: -97.108,
      address: '100 Main St, Fort Worth, TX',
      driverName: 'Jane Trucker',
      mediaUrl: 'https://example.com/clip.mp4',
    },
  ];

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
    expect(store.routeHistory()).toBeNull();
    expect(store.routeHistoryStatus()).toBe('idle');
    expect(store.safetyEvents()).toBeNull();
    expect(store.safetyEventsStatus()).toBe('idle');
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

  it('loadTruckMapData() populates route history and safety events in parallel on success', async () => {
    const loadPromise = store.loadTruckMapData('truck-1');
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/route-history').flush(routeHistory);
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/safety-events').flush(safetyEvents);
    await loadPromise;

    expect(store.routeHistory()).toEqual(routeHistory);
    expect(store.routeHistoryStatus()).toBe('idle');
    expect(store.safetyEvents()).toEqual(safetyEvents);
    expect(store.safetyEventsStatus()).toBe('idle');
  });

  it('loadTruckMapData() marks only the failing request as error, keeping the other independent', async () => {
    const loadPromise = store.loadTruckMapData('truck-1');
    httpMock
      .expectOne('/api/sw-expedited/trucks/truck-1/route-history')
      .flush(null, { status: 500, statusText: 'Server Error' });
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/safety-events').flush(safetyEvents);
    await loadPromise;

    expect(store.routeHistoryStatus()).toBe('error');
    expect(store.safetyEvents()).toEqual(safetyEvents);
    expect(store.safetyEventsStatus()).toBe('idle');
  });

  it('clearMapData() resets route history and safety events', async () => {
    const loadPromise = store.loadTruckMapData('truck-1');
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/route-history').flush(routeHistory);
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/safety-events').flush(safetyEvents);
    await loadPromise;

    store.clearMapData();

    expect(store.routeHistory()).toBeNull();
    expect(store.routeHistoryStatus()).toBe('idle');
    expect(store.safetyEvents()).toBeNull();
    expect(store.safetyEventsStatus()).toBe('idle');
  });
});
