import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DriverDetailResponse } from '../drivers.models';
import { DriversStore } from '../drivers.store';
import { DriverDetailPage } from './driver-detail.page';

// @angular/google-maps throws in its constructor if `window.google` isn't present at all, so it's stubbed with
// fakes here - DriverDetailPage renders a live DriverLocationMap, which jsdom can't provide a real Maps JS API for.
beforeEach(() => {
  // Must be `function`, not an arrow function - Google Maps constructs these with `new`, which arrow functions
  // can't be used with.
  /* eslint-disable prefer-arrow-callback */
  (window as unknown as { google: unknown }).google = {
    maps: {
      Map: vi.fn(function () {
        return { setCenter: vi.fn(), setZoom: vi.fn() };
      }),
      Marker: vi.fn(function () {
        return { setMap: vi.fn(), setPosition: vi.fn() };
      }),
    },
  };
  /* eslint-enable prefer-arrow-callback */
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('DriverDetailPage', () => {
  let fixture: ComponentFixture<DriverDetailPage>;
  let httpMock: HttpTestingController;

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), DriversStore],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DriverDetailPage);
    fixture.componentRef.setInput('id', 'driver-42');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders the driver detail for the routed :id', async () => {
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('jdoe');
    expect(fixture.nativeElement.textContent).toContain('D1234567');
    expect(fixture.nativeElement.textContent).toContain('Truck 7');
  });

  it('renders the location map when latitude/longitude are present', async () => {
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-driver-location-map')).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('No current location available');
  });

  it('shows a fallback message instead of the map when no current location is available', async () => {
    httpMock
      .expectOne('/api/sw-expedited/drivers/driver-42')
      .flush({ ...detail, latitude: null, longitude: null, formattedLocation: null });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-driver-location-map')).toBeFalsy();
    expect(fixture.nativeElement.textContent).toContain('No current location available');
  });

  it('renders null detail fields as an em dash', async () => {
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush({ ...detail, phone: null, tags: null });
    await fixture.whenStable();
    fixture.detectChanges();

    const dl: string = fixture.nativeElement.querySelector('dl').textContent;
    expect(dl).toContain('—');
  });

  it('renders a labeled mobile back link and an icon-only desktop close link', () => {
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);

    const backLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.lg\\:hidden');
    const closeLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.hidden.lg\\:inline-flex');

    expect(backLink?.textContent).toContain('Back to drivers');
    expect(backLink?.querySelector('ng-icon')?.getAttribute('name')).toBe('lucideMoveLeft');
    expect(closeLink?.getAttribute('aria-label')).toBe('Back to drivers');
    expect(closeLink?.querySelector('ng-icon')?.getAttribute('name')).toBe('lucideX');
  });

  it('clears the selected detail on destroy', async () => {
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    await fixture.whenStable();

    const store = TestBed.inject(DriversStore);
    expect(store.selectedDetail()).toEqual(detail);

    fixture.destroy();

    expect(store.selectedDetail()).toBeNull();
  });
});
