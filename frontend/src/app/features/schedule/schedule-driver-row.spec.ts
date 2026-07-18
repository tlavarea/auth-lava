import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ScheduleDriverRow } from './schedule-driver-row';
import { DriverScheduleRow } from './schedule.models';

// A zone-naive "local wall clock" string, matching the shape of the backend's LocalDateTime fields (e.g.
// pickupAppointmentStart/eta) - `new Date(...)` on this parses back to the same local hour/minute, unlike
// toISOString() which would convert through UTC and shift the hour depending on the runner's timezone offset.
function localWallClock(hour: number, minute: number): string {
  return `2026-07-17T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;
}

describe('ScheduleDriverRow', () => {
  let fixture: ComponentFixture<ScheduleDriverRow>;

  const activeDriver: DriverScheduleRow = {
    driverId: 'driver-42',
    driverName: 'Jane Doe',
    activationStatus: 'active',
    dutyStatus: 'driving',
    manifestStatus: 'manifest_in_progress',
    pickupAppointmentStart: localWallClock(8, 0),
    eta: localWallClock(18, 0),
    origin: '4251 Turin Dr, Bessemer, AL 35020',
    destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
    loadReference: 'SwX-1000589',
  };

  const idleDriver: DriverScheduleRow = {
    driverId: 'driver-7',
    driverName: 'Sam Rivera',
    activationStatus: 'active',
    dutyStatus: 'offDuty',
    manifestStatus: null,
    pickupAppointmentStart: null,
    eta: null,
    origin: null,
    destination: null,
    loadReference: null,
  };

  beforeEach(() => {
    vi.setSystemTime(new Date(2026, 6, 17, 12, 0, 0));
  });

  afterEach(() => vi.useRealTimers());

  async function render(driver: DriverScheduleRow): Promise<void> {
    await TestBed.configureTestingModule({ imports: [ScheduleDriverRow] }).compileComponents();
    fixture = TestBed.createComponent(ScheduleDriverRow);
    fixture.componentRef.setInput('driver', driver);
    fixture.detectChanges();
  }

  it('renders the driver name and duty status badge', async () => {
    await render(activeDriver);

    expect(fixture.nativeElement.textContent).toContain('Jane Doe');
    expect(fixture.nativeElement.textContent).toContain('Driving');
  });

  it('positions a busy segment spanning pickupAppointmentStart to eta within the rolling week', async () => {
    await render(activeDriver);

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    expect(segment).toBeTruthy();
    expect(segment.style.left).toBe('4.761904761904762%'); // 8:00 today of a 7-day (168h) week
  });

  it('shows origin, pickup time, destination, and load reference directly on the busy segment', async () => {
    // A multi-day trip (typical of expedited long-haul loads, unlike the other fixtures' same-day window chosen for
    // predictable leftPercent math) so the segment is comfortably wide enough for both label blocks to render.
    await render({ ...activeDriver, eta: '2026-07-20T10:00:00' });

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    expect(segment.textContent).toContain('Bessemer, AL');
    expect(segment.textContent).toContain('Litchfield Park, AZ');
    expect(segment.textContent).toContain('SwX-1000589');
  });

  it('renders no hover card - the bar itself carries the load details now', async () => {
    await render(activeDriver);

    expect(fixture.nativeElement.querySelector('[data-slot="hover-card-trigger"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-slot="hover-card-content"]')).toBeNull();
  });

  it('hides the origin block on a narrow segment, keeping the destination block visible', async () => {
    await render({ ...activeDriver, pickupAppointmentStart: localWallClock(8, 0), eta: localWallClock(10, 0) });

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/20');
    expect(segment.textContent).not.toContain('Bessemer');
    expect(segment.textContent).toContain('Litchfield Park, AZ');
  });

  it('includes origin, destination, and load reference in the track aria-label', async () => {
    await render(activeDriver);

    const track: HTMLElement = fixture.nativeElement.querySelector('[aria-label]');
    const ariaLabel = track.getAttribute('aria-label');
    expect(ariaLabel).toContain('Jane Doe');
    expect(ariaLabel).toContain('Bessemer, AL 35020');
    expect(ariaLabel).toContain('Litchfield Park, AZ 85340');
    expect(ariaLabel).toContain('SwX-1000589');
  });

  it('renders no busy segment for an idle driver with no matched manifest', async () => {
    await render(idleDriver);

    expect(fixture.nativeElement.querySelector('.bg-success\\/20')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Driving');
  });

  it('renders no duty status badge when dutyStatus is null', async () => {
    await render({ ...idleDriver, dutyStatus: null });

    expect(fixture.nativeElement.querySelector('[hlmBadge]')).toBeNull();
  });
});
