import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverLocationMap } from './driver-location-map';

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
    maps: { Map: mapConstructor, Marker: markerConstructor },
  };
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('DriverLocationMap', () => {
  let fixture: ComponentFixture<DriverLocationMap>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverLocationMap],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverLocationMap);
    fixture.componentRef.setInput('latitude', 35.0527);
    fixture.componentRef.setInput('longitude', -78.8784);
    fixture.componentRef.setInput('formattedLocation', 'Fayetteville, NC');
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

  it('moves the marker when the coordinates change', () => {
    fixture.detectChanges();

    fixture.componentRef.setInput('latitude', 40.7128);
    fixture.componentRef.setInput('longitude', -74.006);
    fixture.detectChanges();

    expect(fakeMarkerInstance.setPosition).toHaveBeenCalledWith({ lat: 40.7128, lng: -74.006 });
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
