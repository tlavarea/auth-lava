import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverLocationMap } from './driver-location-map';

// Mirrors the component's internal ANIMATION_TICK_MS (not exported - an implementation detail) so fake-timer
// advances in these tests land on tick boundaries.
const ANIMATION_TICK_MS_UNDER_TEST = 200;

// jsdom has no real layout/rendering engine for the Google Maps JS API to draw into, and @angular/google-maps
// throws in its constructor if `window.google` isn't present at all, so it's stubbed with fakes here - these tests
// verify DriverLocationMap's own wiring (it constructs the map/marker with the right options, updates the marker
// on input changes), not Google Maps' rendering behavior itself.
let fakeMapInstance: { setCenter: ReturnType<typeof vi.fn>; setZoom: ReturnType<typeof vi.fn> };
let fakeMarkerInstance: { setMap: ReturnType<typeof vi.fn>; setPosition: ReturnType<typeof vi.fn> };
let mapConstructor: ReturnType<typeof vi.fn>;
let markerConstructor: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fakeMapInstance = { setCenter: vi.fn(), setZoom: vi.fn() };
  fakeMarkerInstance = { setMap: vi.fn(), setPosition: vi.fn() };
  // Must be `function`, not an arrow function - Google Maps constructs these with `new`, which arrow functions
  // can't be used with.
  // eslint-disable-next-line prefer-arrow-callback
  mapConstructor = vi.fn(function () {
    return fakeMapInstance;
  });
  // eslint-disable-next-line prefer-arrow-callback
  markerConstructor = vi.fn(function () {
    return fakeMarkerInstance;
  });

  (window as unknown as { google: unknown }).google = {
    maps: { Map: mapConstructor, Marker: markerConstructor, SymbolPath: { FORWARD_CLOSED_ARROW: 1 } },
  };
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('DriverLocationMap', () => {
  let fixture: ComponentFixture<DriverLocationMap>;

  beforeEach(async () => {
    // Enabled before TestBed.createComponent below - the component's constructor subscribes to an RxJS `timer`
    // immediately, which would otherwise register against the real clock and be unreachable by
    // vi.advanceTimersByTime in the animation tests further down.
    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [DriverLocationMap],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverLocationMap);
    fixture.componentRef.setInput('latitude', 35.0527);
    fixture.componentRef.setInput('longitude', -78.8784);
    fixture.componentRef.setInput('formattedLocation', 'Fayetteville, NC');
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('mounts without throwing', () => {
    expect(() => fixture.detectChanges()).not.toThrow();
  });

  it('constructs a Google Map with a marker at the given coordinates', () => {
    fixture.detectChanges();

    expect(mapConstructor).toHaveBeenCalled();
    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({ position: { lat: 35.0527, lng: -78.8784 } })
    );
    expect(fakeMarkerInstance.setMap).toHaveBeenCalledWith(fakeMapInstance);
  });

  it('renders a heading-rotated arrow icon when a heading fix is available', () => {
    fixture.componentRef.setInput('heading', 90);
    fixture.detectChanges();

    expect(markerConstructor).toHaveBeenCalledWith(
      expect.objectContaining({
        icon: expect.objectContaining({
          path: 1,
          rotation: 90,
        }),
      })
    );
  });

  it('falls back to the default pin icon when there is no heading fix', () => {
    fixture.detectChanges();

    expect(markerConstructor).toHaveBeenCalledWith(expect.objectContaining({ position: expect.anything() }));
    expect(markerConstructor.mock.calls[0][0].icon).toBeUndefined();
  });

  it('moves the marker when the coordinates change', () => {
    fixture.detectChanges();

    fixture.componentRef.setInput('latitude', 40.7128);
    fixture.componentRef.setInput('longitude', -74.006);
    fixture.detectChanges();

    expect(fakeMarkerInstance.setPosition).toHaveBeenCalledWith({ lat: 40.7128, lng: -74.006 });
  });

  it('dead-reckons the marker forward between polls while moving', () => {
    fixture.componentRef.setInput('heading', 0); // due north
    fixture.componentRef.setInput('speed', 60); // mph
    fixture.detectChanges();

    vi.advanceTimersByTime(1_000);
    fixture.detectChanges();

    const [firstCall, ...laterCalls] = markerConstructor.mock.calls;
    expect(firstCall[0].position).toEqual({ lat: 35.0527, lng: -78.8784 });
    const latestPosition = fakeMarkerInstance.setPosition.mock.calls.at(-1)?.[0];
    // Heading due north with positive speed should move latitude forward (north = increasing latitude),
    // longitude unchanged.
    expect(latestPosition.lat).toBeGreaterThan(35.0527);
    expect(latestPosition.lng).toBeCloseTo(-78.8784, 6);
    expect(laterCalls.length).toBe(0); // marker isn't reconstructed, just repositioned
  });

  it('does not move the marker when speed is 0 or unset', () => {
    fixture.componentRef.setInput('heading', 0);
    fixture.componentRef.setInput('speed', 0);
    fixture.detectChanges();

    vi.advanceTimersByTime(5_000);
    fixture.detectChanges();

    // setPosition may still be called (each tick rebuilds a fresh position object even when unchanged), but always
    // with the same, un-extrapolated coordinates - a stopped vehicle's marker doesn't drift.
    for (const [position] of fakeMarkerInstance.setPosition.mock.calls) {
      expect(position).toEqual({ lat: 35.0527, lng: -78.8784 });
    }
  });

  it('eases toward a new fix instead of snapping to it', () => {
    fixture.detectChanges();
    vi.advanceTimersByTime(ANIMATION_TICK_MS_UNDER_TEST); // seed tickedPosition at the original fix

    fixture.componentRef.setInput('latitude', 40.7128);
    fixture.componentRef.setInput('longitude', -74.006);
    fixture.detectChanges();
    vi.advanceTimersByTime(ANIMATION_TICK_MS_UNDER_TEST); // one tick toward the new fix, not a full jump
    fixture.detectChanges();

    const latestPosition = fakeMarkerInstance.setPosition.mock.calls.at(-1)?.[0];
    expect(latestPosition.lat).toBeGreaterThan(35.0527);
    expect(latestPosition.lat).toBeLessThan(40.7128);
  });

  it('labels the map container with the formatted location for accessibility', () => {
    fixture.detectChanges();
    const container: HTMLElement | null = fixture.nativeElement.querySelector('google-map[role="img"]');
    expect(container?.getAttribute('aria-label')).toBe('Fayetteville, NC');
  });

  it('falls back to a generic aria-label when no formatted location is available', () => {
    fixture.componentRef.setInput('formattedLocation', null);
    fixture.detectChanges();
    const container: HTMLElement | null = fixture.nativeElement.querySelector('google-map[role="img"]');
    expect(container?.getAttribute('aria-label')).toBe('Driver location map');
  });
});
