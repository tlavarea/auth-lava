import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverLiveLocationResponse } from '@features/drivers/drivers.models';
import { ScheduleManifestMap } from './schedule-manifest-map';
import { ManifestRoute, ManifestSegment } from './schedule.models';

// jsdom has no real layout/rendering engine for the Google Maps JS API, and @angular/google-maps throws in its
// constructor if `window.google` isn't present at all, so it's stubbed with fakes here - same approach as
// driver-location-map.spec.ts. LatLngBounds/fitBounds and Polyline are additionally stubbed since this component
// (unlike DriverLocationMap) fits the map to the route and renders a polyline.
let fakeMapInstance: {
  setCenter: ReturnType<typeof vi.fn>;
  setZoom: ReturnType<typeof vi.fn>;
  fitBounds: ReturnType<typeof vi.fn>;
};
let fakeMarkerInstance: { setMap: ReturnType<typeof vi.fn>; setPosition: ReturnType<typeof vi.fn> };
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
  fakeMapInstance = { setCenter: vi.fn(), setZoom: vi.fn(), fitBounds: vi.fn() };
  fakeMarkerInstance = { setMap: vi.fn(), setPosition: vi.fn() };
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

  const liveLocation: DriverLiveLocationResponse = {
    latitude: 34.5,
    longitude: -100.0,
    heading: 270,
    speed: 62,
    locationTime: '2026-07-18T12:00:00',
    formattedLocation: 'Somewhere, TX',
  };

  async function render(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ScheduleManifestMap],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ScheduleManifestMap);
    fixture.componentRef.setInput('driverId', 'driver-42');
    fixture.componentRef.setInput('manifest', manifest);
    fixture.componentRef.setInput('route', route);
    fixture.detectChanges();
  }

  async function flushLiveLocation(): Promise<void> {
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42/location').flush(liveLocation);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('mounts and fetches the driver live location on init', async () => {
    await render();

    httpMock.expectOne('/api/sw-expedited/drivers/driver-42/location').flush(liveLocation);
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
    fixture.componentRef.setInput('driverId', 'driver-42');
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

  it('renders a heading-rotated driver marker at the live location once loaded', async () => {
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

  it('labels the map with the manifest origin and destination for accessibility', async () => {
    await render();
    await flushLiveLocation();

    const container: HTMLElement | null = fixture.nativeElement.querySelector('google-map[role="img"]');
    expect(container?.getAttribute('aria-label')).toBe(
      'Route map from 4251 Turin Dr, Bessemer, AL 35020 to 6390 N Alsup Rd, Litchfield Park, AZ 85340'
    );
  });
});
