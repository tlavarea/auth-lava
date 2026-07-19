import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DriverScheduleRow } from './schedule.models';
import { SchedulePage } from './schedule.page';

// jsdom has no real layout/rendering engine for the Google Maps JS API, and @angular/google-maps throws in its
// constructor if `window.google` isn't present at all - stubbed here (same approach as
// driver-location-map.spec.ts/schedule-manifest-map.spec.ts) since selecting a manifest mounts a real
// app-schedule-manifest-map.
beforeEach(() => {
  /* eslint-disable prefer-arrow-callback */
  (window as unknown as { google: unknown }).google = {
    maps: {
      Map: vi.fn(function () {
        return {
          setCenter: vi.fn(),
          setZoom: vi.fn(),
          fitBounds: vi.fn(),
          getZoom: vi.fn(() => 6),
          addListener: vi.fn(() => ({ remove: vi.fn() })),
        };
      }),
      Marker: vi.fn(function () {
        return { setMap: vi.fn(), setPosition: vi.fn() };
      }),
      Polyline: vi.fn(function () {
        return { setMap: vi.fn(), setPath: vi.fn(), setOptions: vi.fn() };
      }),
      LatLngBounds: vi.fn(function () {
        return { extend: vi.fn() };
      }),
      SymbolPath: { CIRCLE: 0, FORWARD_CLOSED_ARROW: 1 },
    },
  };
  /* eslint-enable prefer-arrow-callback */
});

afterEach(() => {
  delete (window as unknown as { google?: unknown }).google;
});

describe('SchedulePage', () => {
  let fixture: ComponentFixture<SchedulePage>;
  let httpMock: HttpTestingController;

  const row: DriverScheduleRow = {
    driverId: 'driver-42',
    driverName: 'Jane Doe',
    activationStatus: 'active',
    dutyStatus: 'driving',
    manifests: [
      {
        manifestNumber: 1000589,
        manifestStatus: 'manifest_in_progress',
        pickupAppointmentStart: '2026-07-17T08:00:00',
        eta: '2026-07-20T10:00:00',
        origin: '4251 Turin Dr, Bessemer, AL 35020',
        destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
        loadReference: 'SwX-1000589',
      },
    ],
  };

  beforeEach(async () => {
    vi.setSystemTime(new Date(2026, 6, 17, 12, 0, 0));
    await TestBed.configureTestingModule({
      imports: [SchedulePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SchedulePage);
    fixture.detectChanges();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('renders the nav buttons as outline-variant, left-aligned to the day-grid column offset', () => {
    const header: HTMLElement = fixture.nativeElement.querySelector('header');
    expect(header.className).toContain('grid-cols-[250px_1fr]');

    const navButtons: HTMLButtonElement[] = Array.from(
      header.querySelectorAll('button[aria-label="Previous range"], button[aria-label="Next range"]')
    );
    expect(navButtons).toHaveLength(2);
    for (const button of navButtons) {
      expect(button.className).toContain('border-border'); // outline variant's distinguishing class
    }
  });

  it('shows the current range as a read-only date-range trigger, formatted MM/DD/YYYY - MM/DD/YYYY', () => {
    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector('hlm-date-picker-trigger button');

    expect(trigger.textContent).toContain('07/17/2026 - 07/23/2026');
    expect(trigger.tagName).toBe('BUTTON'); // a button, not a text input - can't be typed into
  });

  it('hides the "Today" button while viewing the default range', () => {
    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    expect(buttons.some((button) => button.textContent?.trim() === 'Today')).toBe(false);
  });

  it('shows the "Today" button after paging away, and it resets the range on click', async () => {
    const previousButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      'button[aria-label="Previous range"]'
    );
    previousButton.click();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await fixture.whenStable();
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const todayButton = buttons.find((button) => button.textContent?.trim() === 'Today');
    expect(todayButton).toBeTruthy();

    todayButton?.click();
    httpMock.expectOne((req) => req.url === '/api/sw-expedited/drivers/timeline').flush([row]);
    await fixture.whenStable();
    fixture.detectChanges();

    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector('hlm-date-picker-trigger button');
    expect(trigger.textContent).toContain('07/17/2026 - 07/23/2026');
  });

  it('shows no route map panel until a manifest is selected', () => {
    expect(fixture.nativeElement.querySelector('app-schedule-manifest-map')).toBeNull();
  });

  it('opens the route map panel below the grid when a manifest segment is clicked', async () => {
    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    segment.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-schedule-manifest-map')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Bessemer, AL');
    expect(fixture.nativeElement.textContent).toContain('Litchfield Park, AZ');

    httpMock.expectOne('/api/sw-expedited/manifests/1000589/route').flush(null);
    httpMock.expectOne('/api/sw-expedited/manifests/1000589/eta').flush(null, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne('/api/sw-expedited/manifests/1000589/driver-location').flush(null);
  });

  it('closes the route map panel when the close button is clicked', async () => {
    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    segment.click();
    fixture.detectChanges();
    httpMock.expectOne('/api/sw-expedited/manifests/1000589/route').flush(null);
    httpMock.expectOne('/api/sw-expedited/manifests/1000589/eta').flush(null, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne('/api/sw-expedited/manifests/1000589/driver-location').flush(null);
    await fixture.whenStable();
    fixture.detectChanges();

    const closeButton: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Close route map"]');
    closeButton.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-schedule-manifest-map')).toBeNull();
  });
});
