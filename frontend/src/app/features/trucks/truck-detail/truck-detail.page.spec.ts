import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TruckDetailResponse, TruckRouteHistoryResponse, TruckSafetyEventEntry } from '../trucks.models';
import { TrucksStore } from '../trucks.store';
import { TruckDetailPage } from './truck-detail.page';

// jsdom has no real layout/rendering engine for the Google Maps JS API, and @angular/google-maps (used by the nested
// app-truck-route-map, rendered whenever hasMapData() is true) throws in its constructor if `window.google` isn't
// present at all - same stub approach as truck-route-map.spec.ts, trimmed to just what mounting the map needs (no
// interaction with it is exercised from this page's own tests).
beforeEach(() => {
  /* eslint-disable prefer-arrow-callback */
  (window as unknown as { google: unknown }).google = {
    maps: {
      Map: vi.fn(function () {
        return { setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn(), getZoom: vi.fn(), addListener: vi.fn() };
      }),
      Marker: vi.fn(function () {
        return { setMap: vi.fn(), setPosition: vi.fn(), setIcon: vi.fn(), addListener: vi.fn() };
      }),
      Polyline: vi.fn(function () {
        return { setMap: vi.fn(), setPath: vi.fn(), setOptions: vi.fn() };
      }),
      LatLngBounds: vi.fn(function () {
        return { extend: vi.fn() };
      }),
      InfoWindow: vi.fn(function () {
        return {
          open: vi.fn(),
          close: vi.fn(),
          setContent: vi.fn(),
          setOptions: vi.fn(),
          get: vi.fn(),
          addListener: vi.fn(),
        };
      }),
      SymbolPath: { CIRCLE: 0, FORWARD_CLOSED_ARROW: 1 },
    },
  };
  /* eslint-enable prefer-arrow-callback */
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('TruckDetailPage', () => {
  let fixture: ComponentFixture<TruckDetailPage>;
  let httpMock: HttpTestingController;

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
    fuelPercent: 62,
    odometerMiles: 184203,
    engineHours: 5412,
    faultCodes: null,
    engineState: 'On',
    ecuSpeedMph: 62.5,
    defLevelPercent: 41,
    batteryVolts: 13.2,
    coolantTempF: 198,
    engineRpm: 1200,
    engineLoadPercent: 54,
    latitude: 35.221,
    longitude: -101.831,
    formattedLocation: 'I-40 near Amarillo, TX',
    locationTime: '2026-07-14T00:00:00',
  };

  const emptyRouteHistory: TruckRouteHistoryResponse = { points: [], stops: [] };

  const routeHistoryWithData: TruckRouteHistoryResponse = {
    points: [{ time: '2026-07-27T12:00:00Z', latitude: 32.735, longitude: -97.108, headingDegrees: 90, speedMph: 45 }],
    stops: [],
  };

  const safetyEvent: TruckSafetyEventEntry = {
    id: 'evt-1',
    occurredAt: '2026-07-27T12:10:00Z',
    behaviorLabels: ['Harsh Brake'],
    latitude: 32.735,
    longitude: -97.108,
    address: '100 Main St, Fort Worth, TX',
    driverName: 'Jane Trucker',
    mediaUrl: null,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TruckDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), TrucksStore],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TruckDetailPage);
    fixture.componentRef.setInput('id', 'truck-1');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  async function flushTruckDetail(
    detailOverride: TruckDetailResponse = detail,
    routeHistoryOverride: TruckRouteHistoryResponse = emptyRouteHistory,
    safetyEventsOverride: TruckSafetyEventEntry[] = []
  ): Promise<void> {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detailOverride);
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/route-history').flush(routeHistoryOverride);
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/safety-events').flush(safetyEventsOverride);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('loads and renders the truck detail for the routed :id', async () => {
    await flushTruckDetail();

    expect(fixture.nativeElement.textContent).toContain('T1000');
    expect(fixture.nativeElement.textContent).toContain('1FUJA6CV12LM12345');
    expect(fixture.nativeElement.textContent).toContain('Jane Trucker');
    expect(fixture.nativeElement.textContent).toContain("T231 - 53' SDL");
    expect(fixture.nativeElement.textContent).toContain('I-40 near Amarillo, TX');
  });

  it('shows a fallback message when no Samsara diagnostics are available', async () => {
    await flushTruckDetail({
      ...detail,
      fuelPercent: null,
      odometerMiles: null,
      engineHours: null,
      engineState: null,
      formattedLocation: null,
    });

    expect(fixture.nativeElement.textContent).toContain('No Samsara diagnostics available for this truck.');
  });

  it('renders null fields as an em dash', async () => {
    await flushTruckDetail({ ...detail, vin: null });

    const dl: string = fixture.nativeElement.querySelector('dl').textContent;
    expect(dl).toContain('—');
  });

  it('renders the header with truck info and a link to the location on Google Maps', async () => {
    await flushTruckDetail();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('T1000');
    const subtitle: string = fixture.nativeElement.querySelector('p.uppercase').textContent.replace(/\s+/g, ' ').trim();
    expect(subtitle).toBe('Vehicle • 2023 Freightliner Cascadia');
    expect(fixture.nativeElement.textContent).toContain('Jane Trucker');
    expect(fixture.nativeElement.textContent).toContain("T231 - 53' SDL");

    const mapsLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector(
      'a[aria-label="Open in Google Maps"]'
    );
    expect(mapsLink?.getAttribute('href')).toBe('https://www.google.com/maps?q=35.221,-101.831');
  });

  it('shows the license plate in the Details section', async () => {
    await flushTruckDetail();

    expect(fixture.nativeElement.textContent).toContain('License Plate');
    expect(fixture.nativeElement.textContent).toContain('6YA522');
  });

  it('shows the current speed in the header when the truck is moving', async () => {
    await flushTruckDetail({ ...detail, engineState: 'On', ecuSpeedMph: 63 });

    expect(fixture.nativeElement.textContent).toContain('63 MPH');
  });

  it('does not show a speed line in the header when the truck is not moving', async () => {
    await flushTruckDetail({ ...detail, engineState: 'Off', ecuSpeedMph: null });

    expect(fixture.nativeElement.textContent).not.toContain('MPH');
  });

  it('does not render a Fault Codes field', async () => {
    await flushTruckDetail();

    expect(fixture.nativeElement.textContent).not.toContain('Fault Codes');
  });

  it('shows Details and Diagnostics as accordion sections', async () => {
    await flushTruckDetail();

    const triggers: (string | undefined)[] = Array.from(
      fixture.nativeElement.querySelectorAll('hlm-accordion-trigger')
    ).map((el) => (el as Element).textContent?.trim());
    expect(triggers).toEqual(['Details', 'Diagnostics']);
  });

  it('shows an error message when the load fails', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(null, { status: 500, statusText: 'Server Error' });
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/route-history').flush(emptyRouteHistory);
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/safety-events').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Couldn't load this truck.");
  });

  it('shows a fallback message when there is no route history or safety events for today', async () => {
    await flushTruckDetail(detail, emptyRouteHistory, []);

    expect(fixture.nativeElement.textContent).toContain('No route history available for today.');
    expect(fixture.nativeElement.querySelector('app-truck-route-map')).toBeNull();
  });

  it('renders the route map when route history has points', async () => {
    await flushTruckDetail(detail, routeHistoryWithData, []);

    expect(fixture.nativeElement.querySelector('app-truck-route-map')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('No route history available for today.');
  });

  it('renders the route map when there are safety events even with no route history', async () => {
    await flushTruckDetail(detail, emptyRouteHistory, [safetyEvent]);

    expect(fixture.nativeElement.querySelector('app-truck-route-map')).not.toBeNull();
  });

  it('clears the selected detail and map data on destroy', async () => {
    await flushTruckDetail(detail, routeHistoryWithData, [safetyEvent]);

    const store = TestBed.inject(TrucksStore);
    expect(store.selectedDetail()).toEqual(detail);
    expect(store.routeHistory()).toEqual(routeHistoryWithData);
    expect(store.safetyEvents()).toEqual([safetyEvent]);

    fixture.destroy();

    expect(store.selectedDetail()).toBeNull();
    expect(store.routeHistory()).toBeNull();
    expect(store.safetyEvents()).toBeNull();
  });
});
