import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TimelineDriverRow } from './timeline-driver-row';
import { DriverTimelineRow } from './timeline.models';

// A zone-naive "local wall clock" string, matching the shape of the backend's LocalDateTime fields (e.g.
// pickupAppointmentStart/eta) - `new Date(...)` on this parses back to the same local hour/minute, unlike
// toISOString() which would convert through UTC and shift the hour depending on the runner's timezone offset.
function localWallClock(hour: number, minute: number): string {
  return `2026-07-17T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`;
}

describe('TimelineDriverRow', () => {
  let fixture: ComponentFixture<TimelineDriverRow>;

  const activeDriver: DriverTimelineRow = {
    driverId: 'driver-42',
    driverName: 'Jane Doe',
    activationStatus: 'active',
    dutyStatus: 'driving',
    manifestStatus: 'manifest_in_progress',
    pickupAppointmentStart: localWallClock(8, 0),
    eta: localWallClock(18, 0),
    destination: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
    loadReference: 'SwX-1000589',
  };

  const idleDriver: DriverTimelineRow = {
    driverId: 'driver-7',
    driverName: 'Sam Rivera',
    activationStatus: 'active',
    dutyStatus: 'offDuty',
    manifestStatus: null,
    pickupAppointmentStart: null,
    eta: null,
    destination: null,
    loadReference: null,
  };

  beforeEach(() => {
    vi.setSystemTime(new Date(2026, 6, 17, 12, 0, 0));
  });

  afterEach(() => vi.useRealTimers());

  async function render(driver: DriverTimelineRow): Promise<void> {
    await TestBed.configureTestingModule({ imports: [TimelineDriverRow] }).compileComponents();
    fixture = TestBed.createComponent(TimelineDriverRow);
    fixture.componentRef.setInput('driver', driver);
    fixture.detectChanges();
  }

  it('renders the driver name and duty status badge', async () => {
    await render(activeDriver);

    expect(fixture.nativeElement.textContent).toContain('Jane Doe');
    expect(fixture.nativeElement.textContent).toContain('Driving');
  });

  it('positions a busy segment spanning pickupAppointmentStart to eta', async () => {
    await render(activeDriver);

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-success\\/70');
    expect(segment).toBeTruthy();
    expect(segment.style.left).toBe('33.33333333333333%'); // 8:00 of 24h
    expect(segment.title).toContain('SwX-1000589');
    expect(segment.title).toContain('6390 N Alsup Rd, Litchfield Park, AZ 85340');
  });

  it('renders no busy segment for an idle driver with no matched manifest', async () => {
    await render(idleDriver);

    expect(fixture.nativeElement.querySelector('.bg-success\\/70')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Driving');
  });

  it('renders no duty status badge when dutyStatus is null', async () => {
    await render({ ...idleDriver, dutyStatus: null });

    expect(fixture.nativeElement.querySelector('[hlmBadge]')).toBeNull();
  });
});
