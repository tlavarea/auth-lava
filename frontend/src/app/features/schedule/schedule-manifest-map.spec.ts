import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ScheduleManifestMap } from './schedule-manifest-map';
import { ManifestDriverLocation, ManifestRoute, ManifestSegment } from './schedule.models';

// jsdom has no real layout/rendering engine for the Google Maps JS API, and @angular/google-maps throws in its
// constructor if `window.google` isn't present at all, so it's stubbed with fakes here - same approach as
// driver-location-map.spec.ts. LatLngBounds/fitBounds and Polyline are additionally stubbed since this component
// (unlike DriverLocationMap) fits the map to the route and renders a polyline.
let fakeMapInstance: {
  setCenter: ReturnType<typeof vi.fn>;
  setZoom: ReturnType<typeof vi.fn>;
  fitBounds: ReturnType<typeof vi.fn>;
  getZoom: ReturnType<typeof vi.fn>;
  addListener: ReturnType<typeof vi.fn>;
};
// Captures listeners registered via fakeMapInstance.addListener (mirroring how @angular/google-maps wires its
// (idle)/(zoomChanged)/etc. outputs - see MapEventManager.getLazyEmitter, which calls `target.addListener(name, fn)`
// directly on the underlying map instance), so tests can simulate the map firing one of those events.
let mapListeners: Record<string, (() => void)[]>;

function fireMapEvent(name: string): void {
  (mapListeners[name] ?? []).forEach((listener) => listener());
}
let fakeMarkerInstance: {
  setMap: ReturnType<typeof vi.fn>;
  setPosition: ReturnType<typeof vi.fn>;
  setIcon: ReturnType<typeof vi.fn>;
};
let fakePolylineInstance: {
  setMap: ReturnType<typeof vi.fn>;
  setPath: ReturnType<typeof vi.fn>;
  setOptions: ReturnType<typeof vi.fn>;
};
let fakeBoundsInstance: { extend: ReturnType<typeof vi.fn> };
let mapConstructor: ReturnType<typeof vi.fn>;
let markerConstructor: ReturnType<typeof vi.fn>;
let polylineConstructor: ReturnType<typeof vi.fn>;
let boundsConstructor: ReturnType<typeof vi.fn>;

beforeEach(() => {
  mapListeners = {};
  fakeMapInstance = {
    setCenter: vi.fn(),
    setZoom: vi.fn(),
    fitBounds: vi.fn(),
    getZoom: vi.fn(() => 6),
    addListener: vi.fn((name: string, listener: () => void) => {
      (mapListeners[name] ??= []).push(listener);
      return { remove: vi.fn() };
    }),
  };
  fakeMarkerInstance = { setMap: vi.fn(), setPosition: vi.fn(), setIcon: vi.fn() };
  fakePolylineInstance = { setMap: vi.fn(), setPath: vi.fn(), setOptions: vi.fn() };
  fakeBoundsInstance = { extend: vi.fn() };
  // Must be `function`, not an arrow function - Google Maps constructs these with `new`, which arrow functions
  // can't be used with.
  /* eslint-disable prefer-arrow-callback */
  mapConstructor = vi.fn(function () {
    return fakeMapInstance;
  });
  markerConstructor = vi.fn(function () {
    return fakeMarkerInstance;
  });
  polylineConstructor = vi.fn(function () {
    return fakePolylineInstance;
  });
  boundsConstructor = vi.fn(function () {
    return fakeBoundsInstance;
  });
  /* eslint-enable prefer-arrow-callback */

  (window as unknown as { google: unknown }).google = {
    maps: {
      Map: mapConstructor,
      Marker: markerConstructor,
      Polyline: polylineConstructor,
      LatLngBounds: boundsConstructor,
      SymbolPath: { CIRCLE: 0, FORWARD_CLOSED_ARROW: 1 },
    },
  };
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('ScheduleManifestMap', () => {
  let fixture: ComponentFixture<ScheduleManifestMap>;
  let httpMock: HttpTestingController;

  const manifest: ManifestSegment = {
    manifestNumber: 1000589,
    manifestStatus: 'manifest_in_progress',
    pickupAppointmentStart: '2026-07-17T08:00:00',
    eta: '2026-07-20T10:00:00',
    origin: '4251 Turin Dr, Bessemer, AL 35020',
    destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
    loadReference: 'SwX-1000589',
  };

  const route: ManifestRoute = {
    stops: [
      {
        stopId: 'stop-uuid-1',
        sequenceNumber: 1,
        stopType: 'PICKUP',
        siteName: 'Dealer Warehouse',
        address: '4251 Turin Dr, Bessemer, AL 35020',
        latitude: 33.101,
        longitude: -87.99,
        timezoneAbbreviation: 'CDT',
        appointmentWindowStart: '2026-07-17T08:00:00',
        appointmentWindowEnd: '2026-07-17T10:00:00',
        arrivedAt: null,
        checkedInAt: null,
        checkedOutAt: null,
        referenceNumbers: 'CO 01660967',
        notes: null,
        contactPhone: null,
        estimatedMilesToNext: 1800,
        actualMilesToNext: null,
        odometerMiles: 406717,
      },
      {
        stopId: 'stop-uuid-2',
        sequenceNumber: 2,
        stopType: 'DROPOFF',
        siteName: 'Alsup Facility',
        address: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
        latitude: 33.489,
        longitude: -112.361,
        timezoneAbbreviation: 'MST',
        appointmentWindowStart: '2026-07-20T08:00:00',
        appointmentWindowEnd: '2026-07-20T10:00:00',
        arrivedAt: null,
        checkedInAt: null,
        checkedOutAt: null,
        referenceNumbers: 'CO 01660967',
        notes: null,
        contactPhone: null,
        estimatedMilesToNext: null,
        actualMilesToNext: null,
        odometerMiles: null,
      },
    ],
    startingPosition: null,
    // Decodes to [(38.5,-120.2), (40.7,-120.95), (43.252,-126.453)] - Google's canonical worked example.
    encodedPolyline: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
    distanceMeters: 1_800_000,
    duration: '64800s',
  };

  const liveLocation: ManifestDriverLocation = {
    latitude: 34.5,
    longitude: -100.0,
    headingDegrees: 270,
    asOf: '2026-07-18T12:00:00',
    formattedLocation: 'Somewhere, TX',
  };

  async function render(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ScheduleManifestMap],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ScheduleManifestMap);
    fixture.componentRef.setInput('manifest', manifest);
    fixture.componentRef.setInput('route', route);
    fixture.detectChanges();
  }

  async function flushLiveLocation(): Promise<void> {
    httpMock.expectOne('/api/sw-expedited/manifests/1000589/driver-location').flush(liveLocation);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('mounts and fetches the driver location once on init', async () => {
    await render();

    httpMock.expectOne('/api/sw-expedited/manifests/1000589/driver-location').flush(liveLocation);
  });

  it('renders a numbered marker per stop at its coordinates', async () => {
    await render();
    await flushLiveLocation();

    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 33.101, lng: -87.99 },
        label: expect.objectContaining({ text: '1' }),
      })
    );
    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 33.489, lng: -112.361 },
        label: expect.objectContaining({ text: '2' }),
      })
    );
  });

  it('marks the first stop as the origin (play icon) and the last stop as the destination (stop icon) when there is no starting position', async () => {
    await render();
    await flushLiveLocation();

    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 33.101, lng: -87.99 },
        icon: expect.objectContaining({ path: 'M -5,-7 L 7,0 L -5,7 Z' }),
      })
    );
    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 33.489, lng: -112.361 },
        icon: expect.objectContaining({ path: 'M -6,-6 L 6,-6 L 6,6 L -6,6 Z' }),
      })
    );
  });

  it('marks the starting position as the origin instead of the first stop when one is present', async () => {
    await TestBed.configureTestingModule({
      imports: [ScheduleManifestMap],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ScheduleManifestMap);
    fixture.componentRef.setInput('manifest', manifest);
    fixture.componentRef.setInput('route', {
      ...route,
      startingPosition: { address: 'Prior stop, GA', latitude: 31.19, longitude: -81.47, note: null },
    });
    fixture.detectChanges();
    await flushLiveLocation();

    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 31.19, lng: -81.47 },
        icon: expect.objectContaining({ path: 'M -5,-7 L 7,0 L -5,7 Z' }),
      })
    );
    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 33.101, lng: -87.99 },
        icon: expect.objectContaining({ fillColor: '#16a34a', path: 0 }),
      })
    );
  });

  it('renders a polyline decoded from the route response', async () => {
    await render();
    await flushLiveLocation();

    expect(polylineConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        path: expect.arrayContaining([expect.objectContaining({ lat: expect.closeTo(38.5, 3) })]),
      })
    );
  });

  it('renders a heading-rotated driver marker at the fetched location once loaded', async () => {
    await render();
    await flushLiveLocation();

    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 34.5, lng: -100.0 },
        icon: expect.objectContaining({ path: 1, rotation: 270 }),
      })
    );
  });

  it('fits the map bounds to the route once loaded', async () => {
    await render();
    await flushLiveLocation();

    expect(boundsConstructor).toHaveBeenCalled();
    expect(fakeBoundsInstance.extend).toHaveBeenCalledWith({ lat: 33.101, lng: -87.99 });
    expect(fakeBoundsInstance.extend).toHaveBeenCalledWith({ lat: 33.489, lng: -112.361 });
    expect(fakeMapInstance.fitBounds).toHaveBeenCalledWith(fakeBoundsInstance);
  });

  // Regression test: Google Maps' fitBounds snaps to its maximum zoom (~21, street level) when given a degenerate
  // bounds box (e.g. a manifest with only one geocoded stop and no starting position) - "zoomed as far in as
  // possible, can't see the route" from a dispatcher's perspective. Clamped defensively via the map's next 'idle'
  // event, regardless of why the auto-fit landed too tight.
  it('clamps the zoom down after the automatic fit lands tighter than the max auto-fit zoom', async () => {
    fakeMapInstance.getZoom.mockReturnValue(21);
    await render();
    await flushLiveLocation();

    fireMapEvent('idle');

    expect(fakeMapInstance.setZoom).toHaveBeenCalledWith(12);
  });

  it('does not clamp the zoom when the automatic fit already lands within a reasonable zoom', async () => {
    fakeMapInstance.getZoom.mockReturnValue(7);
    await render();
    await flushLiveLocation();

    fireMapEvent('idle');

    expect(fakeMapInstance.setZoom).not.toHaveBeenCalled();
  });

  it('does not clamp the zoom on a later idle event unrelated to the automatic fit, e.g. a manual zoom', async () => {
    fakeMapInstance.getZoom.mockReturnValue(21);
    await render();
    await flushLiveLocation();
    fireMapEvent('idle');
    fakeMapInstance.setZoom.mockClear();

    fireMapEvent('idle');

    expect(fakeMapInstance.setZoom).not.toHaveBeenCalled();
  });

  // Regression test: this map used to re-poll the driver's location on a 15s timer for a "live tracking" marker -
  // removed in favor of a single fetch, since a dispatcher who wants actual live tracking now clicks the driver's
  // name instead (opening driver-detail's continuously-polled map). Confirms no second request goes out unprompted.
  it('does not re-fetch the driver location after the initial fetch, even after 15s pass', async () => {
    vi.useFakeTimers();
    try {
      await render();
      httpMock.expectOne('/api/sw-expedited/manifests/1000589/driver-location').flush(liveLocation);
      await fixture.whenStable();
      fixture.detectChanges();

      await vi.advanceTimersByTimeAsync(15_000);

      httpMock.expectNone('/api/sw-expedited/manifests/1000589/driver-location');
    } finally {
      vi.useRealTimers();
    }
  });

  it('labels the map with the manifest origin and destination for accessibility', async () => {
    await render();
    await flushLiveLocation();

    const container: HTMLElement | null = fixture.nativeElement.querySelector('google-map[role="img"]');
    expect(container?.getAttribute('aria-label')).toBe(
      'Route map from 4251 Turin Dr, Bessemer, AL 35020 to 6390 N Alsup Rd, Litchfield Park, AZ 85340'
    );
  });
});
