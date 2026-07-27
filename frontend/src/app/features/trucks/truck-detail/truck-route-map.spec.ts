import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { TruckRoutePoint, TruckRouteStop, TruckSafetyEventEntry } from '../trucks.models';
import { TruckRouteMap } from './truck-route-map';

// jsdom has no real layout/rendering engine for the Google Maps JS API, and @angular/google-maps throws in its
// constructor if `window.google` isn't present at all, so it's stubbed with fakes here - same approach as
// schedule-manifest-map.spec.ts/driver-location-map.spec.ts. InfoWindow is additionally stubbed since this
// component (unlike either of those) uses a shared <map-info-window> for hover tooltips.
let fakeMapInstance: {
  setCenter: ReturnType<typeof vi.fn>;
  setZoom: ReturnType<typeof vi.fn>;
  fitBounds: ReturnType<typeof vi.fn>;
  getZoom: ReturnType<typeof vi.fn>;
  addListener: ReturnType<typeof vi.fn>;
};
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
let fakeInfoWindowInstance: {
  open: ReturnType<typeof vi.fn>;
  close: ReturnType<typeof vi.fn>;
  setContent: ReturnType<typeof vi.fn>;
  get: ReturnType<typeof vi.fn>;
};
let mapConstructor: ReturnType<typeof vi.fn>;
let markerConstructor: ReturnType<typeof vi.fn>;
let polylineConstructor: ReturnType<typeof vi.fn>;
let boundsConstructor: ReturnType<typeof vi.fn>;
let infoWindowConstructor: ReturnType<typeof vi.fn>;

beforeEach(() => {
  mapListeners = {};
  fakeMapInstance = {
    setCenter: vi.fn(),
    setZoom: vi.fn(),
    fitBounds: vi.fn(),
    getZoom: vi.fn(() => 10),
    addListener: vi.fn((name: string, listener: () => void) => {
      (mapListeners[name] ??= []).push(listener);
      return { remove: vi.fn() };
    }),
  };
  fakeMarkerInstance = { setMap: vi.fn(), setPosition: vi.fn(), setIcon: vi.fn() };
  fakePolylineInstance = { setMap: vi.fn(), setPath: vi.fn(), setOptions: vi.fn() };
  fakeBoundsInstance = { extend: vi.fn() };
  fakeInfoWindowInstance = { open: vi.fn(), close: vi.fn(), setContent: vi.fn(), get: vi.fn(() => undefined) };
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
  infoWindowConstructor = vi.fn(function () {
    return fakeInfoWindowInstance;
  });
  /* eslint-enable prefer-arrow-callback */

  (window as unknown as { google: unknown }).google = {
    maps: {
      Map: mapConstructor,
      Marker: markerConstructor,
      Polyline: polylineConstructor,
      LatLngBounds: boundsConstructor,
      InfoWindow: infoWindowConstructor,
      SymbolPath: { CIRCLE: 0, FORWARD_CLOSED_ARROW: 1 },
    },
  };
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('TruckRouteMap', () => {
  let fixture: ComponentFixture<TruckRouteMap>;

  const points: TruckRoutePoint[] = [
    { time: '2026-07-27T12:00:00Z', latitude: 32.7, longitude: -97.0, headingDegrees: 90, speedMph: 45 },
    { time: '2026-07-27T12:05:00Z', latitude: 32.735, longitude: -97.108, headingDegrees: 180, speedMph: 0 },
  ];

  const stops: TruckRouteStop[] = [
    {
      latitude: 32.735,
      longitude: -97.108,
      formattedLocation: 'Fort Worth, TX',
      arrivalTime: '2026-07-27T12:05:00Z',
      departureTime: '2026-07-27T12:20:00Z',
      stoppedMinutes: 15,
    },
  ];

  const safetyEvents: TruckSafetyEventEntry[] = [
    {
      id: 'evt-1',
      occurredAt: '2026-07-27T12:10:00Z',
      behaviorLabels: ['Harsh Brake'],
      latitude: 32.75,
      longitude: -97.12,
      address: '100 Main St, Fort Worth, TX',
      driverName: 'Jane Trucker',
      mediaUrl: 'https://example.com/clip.mp4',
    },
  ];

  async function render(
    overrides: {
      points?: TruckRoutePoint[];
      stops?: TruckRouteStop[];
      safetyEvents?: TruckSafetyEventEntry[];
      driverName?: string | null;
    } = {}
  ): Promise<void> {
    await TestBed.configureTestingModule({ imports: [TruckRouteMap] }).compileComponents();

    fixture = TestBed.createComponent(TruckRouteMap);
    fixture.componentRef.setInput('points', overrides.points ?? points);
    fixture.componentRef.setInput('stops', overrides.stops ?? stops);
    fixture.componentRef.setInput('safetyEvents', overrides.safetyEvents ?? safetyEvents);
    fixture.componentRef.setInput('driverName', overrides.driverName ?? 'Jane Trucker');
    fixture.detectChanges();
  }

  it('renders a polyline through the raw route points', async () => {
    await render();

    expect(polylineConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        path: [
          { lat: 32.7, lng: -97.0 },
          { lat: 32.735, lng: -97.108 },
        ],
      })
    );
  });

  it('does not render a polyline when there are no points', async () => {
    await render({ points: [] });

    expect(polylineConstructor).not.toHaveBeenCalled();
  });

  it('renders a bullseye-icon marker per stop at its centroid', async () => {
    await render();

    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 32.735, lng: -97.108 },
        icon: expect.objectContaining({ url: expect.stringContaining('data:image/svg+xml') }),
      })
    );
  });

  it("renders a green play-button marker at the truck's current (most recent) location", async () => {
    await render();

    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        position: { lat: 32.735, lng: -97.108 },
        icon: expect.objectContaining({ path: 'M -5,-7 L 7,0 L -5,7 Z', fillColor: '#16a34a' }),
      })
    );
  });

  it('does not render a current-location marker when there are no points', async () => {
    await render({ points: [] });

    expect(markerConstructor).not.toHaveBeenCalledWith(
      expect.objectContaining({
        icon: expect.objectContaining({ path: 'M -5,-7 L 7,0 L -5,7 Z' }),
      })
    );
  });

  it('renders a distinct icon for safety events than for stops', async () => {
    await render();

    const stopCall = markerConstructor.mock.calls.find(
      ([config]) => config.position.lat === 32.735 && config.position.lng === -97.108
    );
    const eventCall = markerConstructor.mock.calls.find(
      ([config]) => config.position.lat === 32.75 && config.position.lng === -97.12
    );

    expect(stopCall).toBeDefined();
    expect(eventCall).toBeDefined();
    expect(stopCall![0].icon.url).not.toEqual(eventCall![0].icon.url);
  });

  it('fits the map bounds to every point, stop, and safety event', async () => {
    await render();

    expect(boundsConstructor).toHaveBeenCalled();
    expect(fakeBoundsInstance.extend).toHaveBeenCalledWith({ lat: 32.7, lng: -97.0 });
    expect(fakeBoundsInstance.extend).toHaveBeenCalledWith({ lat: 32.735, lng: -97.108 });
    expect(fakeBoundsInstance.extend).toHaveBeenCalledWith({ lat: 32.75, lng: -97.12 });
    expect(fakeMapInstance.fitBounds).toHaveBeenCalledWith(fakeBoundsInstance);
  });

  // Regression test: fitBounds snapping to Google's max zoom (~21, street level) on a degenerate bounds box (e.g. a
  // truck that never moved) - same defense as schedule-manifest-map.ts's identical regression test.
  it('clamps the zoom down after the automatic fit lands tighter than the max auto-fit zoom', async () => {
    fakeMapInstance.getZoom.mockReturnValue(21);
    await render();

    fireMapEvent('idle');

    expect(fakeMapInstance.setZoom).toHaveBeenCalledWith(14);
  });

  it('opens the shared info window on marker hover and closes it after mouseout', async () => {
    vi.useFakeTimers();
    try {
      await render();
      // The template renders the stop's @for block before the safety events' @for block, so the first
      // <map-marker> is always the (single) stop in this fixture's data.
      const [stopMarkerDebugEl] = fixture.debugElement.queryAll(By.css('map-marker'));
      expect(stopMarkerDebugEl).toBeDefined();

      stopMarkerDebugEl.triggerEventHandler('mapMouseover', {});
      fixture.detectChanges();
      expect(fakeInfoWindowInstance.open).toHaveBeenCalled();

      stopMarkerDebugEl.triggerEventHandler('mapMouseout', {});
      await vi.advanceTimersByTimeAsync(100);
      fixture.detectChanges();

      expect(fakeInfoWindowInstance.close).toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });

  it('labels the map for accessibility', async () => {
    await render();

    const container: HTMLElement | null = fixture.nativeElement.querySelector('google-map[role="img"]');
    expect(container?.getAttribute('aria-label')).toBe('Truck route map');
  });
});
