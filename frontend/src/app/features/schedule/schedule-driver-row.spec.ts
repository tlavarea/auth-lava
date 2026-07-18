import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WEEK_MS } from './schedule-chart';
import { ScheduleDriverRow } from './schedule-driver-row';
import { DriverScheduleRow, ManifestSegment } from './schedule.models';

// A zone-naive "local wall clock" string, matching the shape of the backend's LocalDateTime fields (e.g.
// pickupAppointmentStart/eta) - `new Date(...)` on this parses back to the same local hour/minute, unlike
// toISOString() which would convert through UTC and shift the hour depending on the runner's timezone offset.
function localWallClock(hour: number, minute: number): string {
  return `2026-07-17T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;
}

describe('ScheduleDriverRow', () => {
  let fixture: ComponentFixture<ScheduleDriverRow>;

  const weekStart = new Date(2026, 6, 17, 0, 0, 0).getTime();

  const activeManifest: ManifestSegment = {
    manifestStatus: 'manifest_in_progress',
    pickupAppointmentStart: localWallClock(8, 0),
    eta: localWallClock(18, 0),
    origin: '4251 Turin Dr, Bessemer, AL 35020',
    destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
    loadReference: 'SwX-1000589',
  };

  const activeDriver: DriverScheduleRow = {
    driverId: 'driver-42',
    driverName: 'Jane Doe',
    activationStatus: 'active',
    dutyStatus: 'driving',
    manifests: [activeManifest],
  };

  const idleDriver: DriverScheduleRow = {
    driverId: 'driver-7',
    driverName: 'Sam Rivera',
    activationStatus: 'active',
    dutyStatus: 'offDuty',
    manifests: [],
  };

  beforeEach(() => {
    vi.setSystemTime(new Date(2026, 6, 17, 12, 0, 0));
  });

  afterEach(() => vi.useRealTimers());

  async function render(driver: DriverScheduleRow, weekStartMs: number = weekStart): Promise<void> {
    await TestBed.configureTestingModule({ imports: [ScheduleDriverRow] }).compileComponents();
    fixture = TestBed.createComponent(ScheduleDriverRow);
    fixture.componentRef.setInput('driver', driver);
    fixture.componentRef.setInput('weekStart', weekStartMs);
    fixture.detectChanges();
  }

  it('renders the driver name and duty status badge', async () => {
    await render(activeDriver);

    expect(fixture.nativeElement.textContent).toContain('Jane Doe');
    expect(fixture.nativeElement.textContent).toContain('Driving');
  });

  it('positions a busy segment spanning pickupAppointmentStart to eta within the visible week', async () => {
    await render(activeDriver);

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    expect(segment).toBeTruthy();
    expect(segment.style.left).toBe('4.761904761904762%'); // 8:00 on day 0 of a 7-day (168h) week
  });

  it('shows origin, pickup time, destination, and load reference directly on the busy segment', async () => {
    // A multi-day trip (typical of expedited long-haul loads, unlike the other fixtures' same-day window chosen for
    // predictable leftPercent math) so the segment is comfortably wide enough for both label blocks to render.
    await render({
      ...activeDriver,
      manifests: [{ ...activeManifest, eta: '2026-07-20T10:00:00' }],
    });

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    expect(segment.textContent).toContain('Bessemer, AL');
    expect(segment.textContent).toContain('Litchfield Park, AZ');
    expect(segment.textContent).toContain('SwX-1000589');
  });

  it('renders one busy segment per manifest for a driver with multiple loads in the week', async () => {
    const secondManifest: ManifestSegment = {
      ...activeManifest,
      pickupAppointmentStart: '2026-07-19T08:00:00',
      eta: '2026-07-19T18:00:00',
      loadReference: 'SwX-1000600',
    };
    await render({ ...activeDriver, manifests: [activeManifest, secondManifest] });

    const segments: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.bg-success\\/20');
    expect(segments).toHaveLength(2);
  });

  it('renders no hover card - the bar itself carries the load details now', async () => {
    await render(activeDriver);

    expect(fixture.nativeElement.querySelector('[data-slot="hover-card-trigger"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-slot="hover-card-content"]')).toBeNull();
  });

  it('hides the origin block on a narrow segment, keeping the destination block visible', async () => {
    await render({
      ...activeDriver,
      manifests: [{ ...activeManifest, pickupAppointmentStart: localWallClock(8, 0), eta: localWallClock(10, 0) }],
    });

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    expect(segment.textContent).not.toContain('Bessemer');
    expect(segment.textContent).toContain('Litchfield Park, AZ');
  });

  it('includes the driver name and load count in the track aria-label', async () => {
    await render(activeDriver);

    const track: HTMLElement = fixture.nativeElement.querySelector('[aria-label]');
    const ariaLabel = track.getAttribute('aria-label');
    expect(ariaLabel).toContain('Jane Doe');
    expect(ariaLabel).toContain('1 load');
  });

  it('includes origin, destination, and load reference in each segment aria-label', async () => {
    await render(activeDriver);

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    const ariaLabel = segment.getAttribute('aria-label');
    expect(ariaLabel).toContain('Bessemer, AL 35020');
    expect(ariaLabel).toContain('Litchfield Park, AZ 85340');
    expect(ariaLabel).toContain('SwX-1000589');
  });

  it('renders no busy segment for an idle driver with no matched manifest', async () => {
    await render(idleDriver);

    expect(fixture.nativeElement.querySelector('.bg-success\\/20')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Driving');
  });

  it('shows "idle" in the track aria-label for a driver with no manifests', async () => {
    await render(idleDriver);

    const track: HTMLElement = fixture.nativeElement.querySelector('[aria-label]');
    expect(track.getAttribute('aria-label')).toBe('Sam Rivera: idle');
  });

  it('renders no duty status badge when dutyStatus is null', async () => {
    await render({ ...idleDriver, dutyStatus: null });

    expect(fixture.nativeElement.querySelector('[hlmBadge]')).toBeNull();
  });

  it('shows the now marker when viewing the week containing the current time', async () => {
    await render(activeDriver, weekStart);

    expect(fixture.nativeElement.querySelector('.bg-foreground\\/60')).toBeTruthy();
  });

  it('hides the now marker when viewing a different week', async () => {
    await render(activeDriver, weekStart - WEEK_MS);

    expect(fixture.nativeElement.querySelector('.bg-foreground\\/60')).toBeNull();
  });
});
