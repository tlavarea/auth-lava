import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { DriverDetailPage } from './driver-detail/driver-detail.page';
import { DriverDetailResponse, DriverListingRow } from './drivers.models';
import { DriversPage } from './drivers.page';

// jsdom doesn't implement matchMedia, and CDK's BreakpointObserver depends on it directly.
function stubMatchMedia(matches: boolean): void {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

// @angular/google-maps throws in its constructor if `window.google` isn't present at all, so it's stubbed with
// fakes here - navigating into a driver detail route renders a live DriverLocationMap, which jsdom can't provide a
// real Maps JS API for.
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
      SymbolPath: { FORWARD_CLOSED_ARROW: 1 },
    },
  };
  /* eslint-enable prefer-arrow-callback */
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('DriversPage', () => {
  let fixture: ComponentFixture<DriversPage>;
  let httpMock: HttpTestingController;

  const drivers: DriverListingRow[] = [
    {
      id: 'driver-42',
      name: 'Jane Doe',
      activationStatus: 'active',
      currentVehicleName: 'Truck 7',
      dutyStatus: 'driving',
      currentLocation: 'Fort Worth, TX',
    },
  ];

  async function createFixture(desktop: boolean): Promise<void> {
    stubMatchMedia(desktop);

    await TestBed.configureTestingModule({
      imports: [DriversPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DriversPage);
    fixture.detectChanges();

    httpMock.expectOne('/api/sw-expedited/drivers').flush(drivers);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', async () => {
    await createFixture(true);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the table on desktop', async () => {
    await createFixture(true);
    expect(fixture.nativeElement.querySelector('app-driver-table')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-driver-item')).toBeFalsy();
  });

  it('renders items on mobile', async () => {
    await createFixture(false);
    expect(fixture.nativeElement.querySelector('app-driver-item')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-driver-table')).toBeFalsy();
  });

  it('keeps the mobile detail sheet off-screen, inert, with no backdrop when no detail route is active', async () => {
    await createFixture(false);

    expect(fixture.nativeElement.querySelector('.translate-x-full')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[inert]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[class*="bg-black/50"]')).toBeFalsy();
  });

  it('collapses the detail pane out of layout on desktop when no detail route is active', async () => {
    await createFixture(true);

    expect(fixture.nativeElement.querySelector('.hidden')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[class*="bg-black/50"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('app-driver-table')).toBeTruthy();
  });

  it('shows an inline master/detail split on desktop when a detail route is active', async () => {
    stubMatchMedia(true);

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
      driveRemainingDurationMs: null,
      shiftRemainingDurationMs: null,
      cycleRemainingDurationMs: null,
      timeUntilBreakDurationMs: null,
      dutyStatusSince: null,
      tags: null,
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

    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(
          [{ path: '', component: DriversPage, children: [{ path: ':id', component: DriverDetailPage }] }],
          withComponentInputBinding()
        ),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/driver-42', DriversPage);

    httpMock.expectOne('/api/sw-expedited/drivers').flush(drivers);
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detail);
    // loadDriverActivity is sequenced after loadDriverDetail resolves (see DriverDetailPage's constructor), so the
    // activity request doesn't exist yet until a few stability ticks after the detail response is flushed.
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();
    httpMock.expectOne((req) => req.url.startsWith('/api/sw-expedited/drivers/driver-42/activity')).flush([]);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();

    const root: HTMLElement = harness.routeNativeElement!;
    expect(root.querySelector('app-driver-item')).toBeTruthy();
    expect(root.querySelector('app-driver-table')).toBeFalsy();
    expect(root.querySelector('router-outlet')?.parentElement?.classList.contains('hidden')).toBe(false);
    expect(root.querySelector('[class*="bg-black/50"]')).toBeFalsy();
    expect(root.querySelector('[aria-current="page"]')).toBeTruthy();
  });
});
