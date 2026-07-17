import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DriverDetailResponse } from '../drivers.models';
import { DriversStore } from '../drivers.store';
import { DriverDetailPage } from './driver-detail.page';

// @angular/google-maps throws in its constructor if `window.google` isn't present at all, so it's stubbed with
// fakes here - DriverDetailPage renders a live DriverLocationMap, which jsdom can't provide a real Maps JS API for.
// No ControlPosition here deliberately - Google's real Maps JS API only populates enums like ControlPosition once
// its "maps" library finishes its own async load, so DriverLocationMap's mapOptions must not depend on it either.
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
    driveRemainingDurationMs: 4 * 3_600_000 + 16 * 60_000,
    shiftRemainingDurationMs: 3 * 3_600_000 + 58 * 60_000,
    cycleRemainingDurationMs: 22 * 3_600_000 + 58 * 60_000,
    timeUntilBreakDurationMs: 5 * 3_600_000 + 38 * 60_000,
    dutyStatusSince: new Date(Date.now() - (1 * 3_600_000 + 43 * 60_000)).toISOString(),
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

  // Every test triggers both the detail and activity-feed loads on init (see DriverDetailPage's constructor
  // effect) - flushed here so httpMock.verify() doesn't flag either request as unmatched. loadDriverActivity is
  // sequenced after loadDriverDetail resolves (not fired in parallel - see the constructor's comment), so the
  // activity request doesn't exist yet until a few stability ticks after the detail response is flushed (one CD
  // cycle per hop of the await chain: loadDriverDetail's own promise, then the constructor's continuation, then
  // loadDriverActivity issuing its HTTP call) - a single fixture.whenStable() isn't reliably enough ticks.
  async function flushDetailOnly(detailResponse: DriverDetailResponse): Promise<void> {
    httpMock.expectOne('/api/sw-expedited/drivers/driver-42').flush(detailResponse);
    await fixture.whenStable();
    await fixture.whenStable();
    await fixture.whenStable();
  }

  async function flushDetail(detailResponse: DriverDetailResponse): Promise<void> {
    await flushDetailOnly(detailResponse);
    httpMock.expectOne((req) => req.url.startsWith('/api/sw-expedited/drivers/driver-42/activity')).flush([]);
  }

  it('loads and renders the driver detail for the routed :id', async () => {
    await flushDetail(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('jdoe');
    expect(fixture.nativeElement.textContent).toContain('D1234567');
    expect(fixture.nativeElement.textContent).toContain('Truck 7');
  });

  it('renders the location map when latitude/longitude are present', async () => {
    await flushDetail(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-driver-location-map')).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('No current location available');
  });

  it('shows a fallback message instead of the map when no current location is available', async () => {
    await flushDetail({ ...detail, latitude: null, longitude: null, formattedLocation: null });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-driver-location-map')).toBeFalsy();
    expect(fixture.nativeElement.textContent).toContain('No current location available');
  });

  it('renders the HOS clock rings and current duty status elapsed time', async () => {
    await flushDetail(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Hours of Service');
    expect(fixture.nativeElement.textContent).toContain('Driving');
    expect(fixture.nativeElement.querySelectorAll('app-hos-clock-ring').length).toBe(4);
    expect(fixture.nativeElement.textContent).toContain('4:16');
    expect(fixture.nativeElement.textContent).toContain('1:43');
  });

  it('shows a fallback message instead of HOS clocks when no duty status is available', async () => {
    await flushDetail({
      ...detail,
      dutyStatus: null,
      driveRemainingDurationMs: null,
      shiftRemainingDurationMs: null,
      cycleRemainingDurationMs: null,
      timeUntilBreakDurationMs: null,
      dutyStatusSince: null,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('app-hos-clock-ring').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('No HOS data available');
  });

  it('renders null detail fields as an em dash', async () => {
    await flushDetail({ ...detail, phone: null, tags: null });
    await fixture.whenStable();
    fixture.detectChanges();

    const dl: string = fixture.nativeElement.querySelector('dl').textContent;
    expect(dl).toContain('—');
  });

  it('renders a labeled mobile back link and an icon-only desktop close link', async () => {
    await flushDetail(detail);

    const backLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.lg\\:hidden');
    const closeLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.hidden.lg\\:inline-flex');

    expect(backLink?.textContent).toContain('Back to drivers');
    expect(backLink?.querySelector('ng-icon')?.getAttribute('name')).toBe('lucideMoveLeft');
    expect(closeLink?.getAttribute('aria-label')).toBe('Back to drivers');
    expect(closeLink?.querySelector('ng-icon')?.getAttribute('name')).toBe('lucideX');
  });

  it('renders the activity feed from the activity endpoint', async () => {
    await flushDetailOnly(detail);
    httpMock
      .expectOne((req) => req.url.startsWith('/api/sw-expedited/drivers/driver-42/activity'))
      .flush([
        {
          dutyStatus: 'driving',
          // A real UTC instant (as the backend now sends), constructed from this local wall-clock time so the
          // "11:04 AM" assertion below holds on any CI runner's timezone.
          startTime: new Date(2026, 6, 16, 11, 4, 0).toISOString(),
          endTime: null,
          latitude: 27.9,
          longitude: -81.6,
          remark: null,
        },
      ]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-driver-activity-feed')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Activity');
    expect(fixture.nativeElement.textContent).toContain('11:04 AM');
  });

  // Regression test for the constructor's sequencing (detail, then activity, not fired in parallel) - see its
  // comment for why: two near-simultaneous requests on a first/cold load could race a session-refresh retry.
  it('does not request activity until the detail request has resolved', async () => {
    expect(httpMock.match((req) => req.url.startsWith('/api/sw-expedited/drivers/driver-42/activity')).length).toBe(0);

    await flushDetailOnly(detail);

    httpMock.expectOne((req) => req.url.startsWith('/api/sw-expedited/drivers/driver-42/activity')).flush([]);
  });

  it('shows a loading indicator for the activity panel until the activity request resolves', async () => {
    await flushDetailOnly(detail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('hlm-spinner')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-driver-activity-feed')).toBeFalsy();

    httpMock.expectOne((req) => req.url.startsWith('/api/sw-expedited/drivers/driver-42/activity')).flush([]);
    await fixture.whenStable();
  });

  it('shows an error message for the activity panel instead of a false "no activity" empty state', async () => {
    await flushDetailOnly(detail);

    httpMock
      .expectOne((req) => req.url.startsWith('/api/sw-expedited/drivers/driver-42/activity'))
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Couldn't load activity.");
    expect(fixture.nativeElement.querySelector('app-driver-activity-feed')).toBeFalsy();
  });

  it('opens the Hours of Service accordion expanded by default', async () => {
    await flushDetail(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const triggers: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('hlm-accordion-trigger'));
    const hosTrigger = triggers.find((t) => t.textContent?.includes('Hours of Service'));
    expect(hosTrigger?.querySelector('button')?.getAttribute('aria-expanded')).toBe('true');
  });

  it('renders the mobile Activity accordion closed by default and the desktop panel separately', async () => {
    await flushDetail(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const triggers: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('hlm-accordion-trigger'));
    const activityTrigger = triggers.find((t) => t.textContent?.includes('Activity'));
    expect(activityTrigger?.querySelector('button')?.getAttribute('aria-expanded')).toBe('false');

    // Rendered twice - once in the always-expanded desktop floating panel, once inside the mobile accordion.
    expect(fixture.nativeElement.querySelectorAll('app-driver-activity-feed').length).toBe(2);
  });

  it('renders the desktop Activity panel hidden below lg and floating over the map above it', async () => {
    await flushDetail(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const desktopPanel: HTMLElement | null = fixture.nativeElement.querySelector('section.absolute.lg\\:flex');
    expect(desktopPanel).toBeTruthy();
    expect(desktopPanel?.classList.contains('hidden')).toBe(true);
  });

  it('clears the selected detail on destroy', async () => {
    await flushDetail(detail);
    await fixture.whenStable();

    const store = TestBed.inject(DriversStore);
    expect(store.selectedDetail()).toEqual(detail);

    fixture.destroy();

    expect(store.selectedDetail()).toBeNull();
  });
});
