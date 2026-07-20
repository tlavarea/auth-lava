import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DAY_MS, DEFAULT_RANGE_DAYS } from './schedule-chart';
import { ScheduleDriverRow } from './schedule-driver-row';
import { DriverScheduleRow, ManifestSegment, TimeOffSegment } from './schedule.models';

const WEEK_MS = DEFAULT_RANGE_DAYS * DAY_MS;

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
    manifestNumber: 1000589,
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
    timeOff: [],
  };

  const idleDriver: DriverScheduleRow = {
    driverId: 'driver-7',
    driverName: 'Sam Rivera',
    activationStatus: 'active',
    dutyStatus: 'offDuty',
    manifests: [],
    timeOff: [],
  };

  const vacationTimeOff: TimeOffSegment = {
    id: 'time-off-1',
    startAt: localWallClock(8, 0),
    endAt: localWallClock(18, 0),
    reason: 'Vacation',
  };

  beforeEach(() => {
    vi.setSystemTime(new Date(2026, 6, 17, 12, 0, 0));
  });

  afterEach(() => vi.useRealTimers());

  async function render(
    driver: DriverScheduleRow,
    rangeStartMs: number = weekStart,
    rangeDays: number = DEFAULT_RANGE_DAYS
  ): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ScheduleDriverRow],
      providers: [provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(ScheduleDriverRow);
    fixture.componentRef.setInput('driver', driver);
    fixture.componentRef.setInput('rangeStart', rangeStartMs);
    fixture.componentRef.setInput('rangeDays', rangeDays);
    fixture.detectChanges();
  }

  it('renders the driver name and duty status badge', async () => {
    await render(activeDriver);

    expect(fixture.nativeElement.textContent).toContain('Jane Doe');
    expect(fixture.nativeElement.textContent).toContain('Driving');
  });

  it('links the driver name to their driver-detail page', async () => {
    await render(activeDriver);

    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a');
    expect(link?.textContent?.trim()).toBe('Jane Doe');
    expect(link?.getAttribute('href')).toBe('/drivers/driver-42');
  });

  it('positions a busy segment spanning pickupAppointmentStart to eta within the visible week', async () => {
    await render(activeDriver);

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
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

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    expect(segment.textContent).toContain('Bessemer, AL');
    expect(segment.textContent).toContain('Litchfield Park, AZ');
    expect(segment.textContent).toContain('SwX-1000589');
  });

  it('renders one busy segment per manifest for a driver with multiple loads in the week', async () => {
    const secondManifest: ManifestSegment = {
      ...activeManifest,
      manifestNumber: 1000600,
      pickupAppointmentStart: '2026-07-19T08:00:00',
      eta: '2026-07-19T18:00:00',
      loadReference: 'SwX-1000600',
    };
    await render({ ...activeDriver, manifests: [activeManifest, secondManifest] });

    const segments: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.bg-info\\/20');
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

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
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

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    const ariaLabel = segment.getAttribute('aria-label');
    expect(ariaLabel).toContain('Bessemer, AL 35020');
    expect(ariaLabel).toContain('Litchfield Park, AZ 85340');
    expect(ariaLabel).toContain('SwX-1000589');
  });

  it('renders no busy segment or time-off bar for an idle driver with nothing scheduled', async () => {
    await render(idleDriver);

    expect(fixture.nativeElement.querySelector('.bg-info\\/20')).toBeNull();
    expect(fixture.nativeElement.querySelector('.bg-time-off\\/15')).toBeNull();
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

  it('shows the now marker for a custom range that contains today without starting on it', async () => {
    await render(activeDriver, weekStart - 3 * DAY_MS, 14);

    expect(fixture.nativeElement.querySelector('.bg-foreground\\/60')).toBeTruthy();
  });

  it('positions a busy segment proportionally to a custom (non-7-day) range length', async () => {
    await render(activeDriver, weekStart, 14);

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    // 8:00 on day 0 of a 14-day (336h) range, half the percent it'd be at in a 7-day range.
    expect(segment.style.left).toBe('2.380952380952381%');
  });

  it("widens the row's min-width to fit every day column at the 138px floor as rangeDays grows", async () => {
    await render(activeDriver, weekStart, 14);

    const row: HTMLElement = fixture.nativeElement.querySelector('.grid');
    expect(row.style.minWidth).toBe(`${250 + 14 * 138}px`);
  });

  it('exposes button semantics on a busy segment for keyboard/AXE accessibility', async () => {
    await render(activeDriver);

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    expect(segment.getAttribute('role')).toBe('button');
    expect(segment.getAttribute('tabindex')).toBe('0');
  });

  it('emits manifestSelected with the driver id and manifest when a busy segment is clicked', async () => {
    await render(activeDriver);
    const emitted: { driverId: string; manifest: ManifestSegment }[] = [];
    fixture.componentInstance.manifestSelected.subscribe((event) => emitted.push(event));

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    segment.click();

    expect(emitted).toEqual([{ driverId: 'driver-42', manifest: activeManifest }]);
  });

  it('emits manifestSelected when Enter is pressed on a focused busy segment', async () => {
    await render(activeDriver);
    const emitted: { driverId: string; manifest: ManifestSegment }[] = [];
    fixture.componentInstance.manifestSelected.subscribe((event) => emitted.push(event));

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    segment.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

    expect(emitted).toEqual([{ driverId: 'driver-42', manifest: activeManifest }]);
  });

  it('emits manifestSelected and prevents page scroll when Space is pressed on a focused busy segment', async () => {
    await render(activeDriver);
    const emitted: { driverId: string; manifest: ManifestSegment }[] = [];
    fixture.componentInstance.manifestSelected.subscribe((event) => emitted.push(event));

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    const spaceEvent = new KeyboardEvent('keydown', { key: ' ', bubbles: true, cancelable: true });
    segment.dispatchEvent(spaceEvent);

    expect(emitted).toEqual([{ driverId: 'driver-42', manifest: activeManifest }]);
    expect(spaceEvent.defaultPrevented).toBe(true);
  });

  it('positions a time-off bar spanning startAt to endAt within the visible week', async () => {
    await render({ ...idleDriver, timeOff: [vacationTimeOff] });

    const bar: HTMLElement = fixture.nativeElement.querySelector('.bg-time-off\\/15');
    expect(bar).toBeTruthy();
    expect(bar.style.left).toBe('4.761904761904762%'); // 8:00 on day 0 of a 7-day (168h) week
  });

  it('shows the reason on a wide time-off bar', async () => {
    await render({ ...idleDriver, timeOff: [{ ...vacationTimeOff, endAt: '2026-07-20T10:00:00' }] });

    const bar: HTMLElement = fixture.nativeElement.querySelector('.bg-time-off\\/15');
    expect(bar.textContent).toContain('Vacation');
  });

  it('hides the reason label on a narrow time-off bar', async () => {
    await render({
      ...idleDriver,
      timeOff: [{ ...vacationTimeOff, startAt: localWallClock(8, 0), endAt: localWallClock(9, 0) }],
    });

    const bar: HTMLElement = fixture.nativeElement.querySelector('.bg-time-off\\/15');
    expect(bar.textContent?.trim()).toBe('');
  });

  it('includes the date range and reason in the time-off bar aria-label', async () => {
    await render({ ...idleDriver, timeOff: [vacationTimeOff] });

    const bar: HTMLElement = fixture.nativeElement.querySelector('.bg-time-off\\/15');
    expect(bar.getAttribute('aria-label')).toContain('Vacation');
  });

  it('renders a busy segment and a time-off bar together without interference', async () => {
    await render({ ...activeDriver, timeOff: [vacationTimeOff] });

    expect(fixture.nativeElement.querySelector('.bg-info\\/20')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.bg-time-off\\/15')).toBeTruthy();
  });

  it('colors manifest_delivered and manifest_tonu segments with the success/green and destructive/red treatments', async () => {
    const delivered: ManifestSegment = { ...activeManifest, manifestNumber: 1, manifestStatus: 'manifest_delivered' };
    const tonu: ManifestSegment = { ...activeManifest, manifestNumber: 2, manifestStatus: 'manifest_tonu' };
    await render({ ...activeDriver, manifests: [delivered, tonu] });

    expect(fixture.nativeElement.querySelector('.bg-success\\/20')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.border-success\\/70')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.bg-destructive\\/20')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.border-destructive\\/70')).toBeTruthy();
  });

  it('falls back to the muted treatment for a status with no assigned color', async () => {
    await render({ ...activeDriver, manifests: [{ ...activeManifest, manifestStatus: 'manifest_planning' }] });

    expect(fixture.nativeElement.querySelector('.bg-muted-foreground\\/15')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.bg-info\\/20')).toBeNull();
    expect(fixture.nativeElement.querySelector('.bg-success\\/20')).toBeNull();
    expect(fixture.nativeElement.querySelector('.bg-destructive\\/20')).toBeNull();
  });

  it('renders one uniform gray tick per day, with no leftover status coloring', async () => {
    await render(activeDriver);

    expect(fixture.nativeElement.querySelectorAll('.bg-neutral-400').length).toBe(DEFAULT_RANGE_DAYS);
    expect(fixture.nativeElement.querySelector('.bg-neutral-300')).toBeNull();
    expect(fixture.nativeElement.querySelector('.bg-success')).toBeNull();
  });

  it('adds vertical margin to busy segments and time-off bars but not to ticks or the now marker', async () => {
    await render({ ...activeDriver, timeOff: [vacationTimeOff] });

    expect(fixture.nativeElement.querySelector('.bg-info\\/20').className).toContain('inset-y-2');
    expect(fixture.nativeElement.querySelector('.bg-time-off\\/15').className).toContain('inset-y-2');
    expect(fixture.nativeElement.querySelector('.bg-neutral-400').className).toContain('inset-y-0');
    expect(fixture.nativeElement.querySelector('.bg-foreground\\/60').className).toContain('inset-y-0');
  });

  it('adds a thicker colored left/right border to a busy segment and a time-off bar that both start and end within the visible range', async () => {
    await render({ ...activeDriver, timeOff: [vacationTimeOff] });

    const segment: HTMLElement = fixture.nativeElement.querySelector('.bg-info\\/20');
    expect(segment.className).toContain('border-l-4');
    expect(segment.className).toContain('border-r-4');
    expect(segment.className).toContain('border-info/70');

    const timeOffBar: HTMLElement = fixture.nativeElement.querySelector('.bg-time-off\\/15');
    expect(timeOffBar.className).toContain('border-l-4');
    expect(timeOffBar.className).toContain('border-r-4');
    expect(timeOffBar.className).toContain('border-time-off/70');
  });

  it('omits the accent border only on the side where a busy segment is actually clipped by the visible range', async () => {
    const clippedStart: ManifestSegment = {
      ...activeManifest,
      manifestNumber: 1,
      pickupAppointmentStart: '2026-07-10T08:00:00',
      eta: localWallClock(10, 0),
    };
    const clippedEnd: ManifestSegment = {
      ...activeManifest,
      manifestNumber: 2,
      pickupAppointmentStart: localWallClock(14, 0),
      eta: '2026-07-30T18:00:00',
    };
    await render({ ...activeDriver, manifests: [clippedStart, clippedEnd] });

    const segments: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.bg-info\\/20');
    expect(segments[0].className).not.toContain('border-l-4');
    expect(segments[0].className).toContain('border-r-4');
    expect(segments[1].className).toContain('border-l-4');
    expect(segments[1].className).not.toContain('border-r-4');
  });

  it('omits the accent border only on the side where a time-off bar is actually clipped by the visible range', async () => {
    const clippedStart: TimeOffSegment = {
      ...vacationTimeOff,
      id: 'time-off-a',
      startAt: '2026-07-10T00:00:00',
      endAt: localWallClock(10, 0),
    };
    const clippedEnd: TimeOffSegment = {
      ...vacationTimeOff,
      id: 'time-off-b',
      startAt: localWallClock(14, 0),
      endAt: '2026-07-30T00:00:00',
    };
    await render({ ...idleDriver, timeOff: [clippedStart, clippedEnd] });

    const bars: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.bg-time-off\\/15');
    expect(bars[0].className).not.toContain('border-l-4');
    expect(bars[0].className).toContain('border-r-4');
    expect(bars[1].className).toContain('border-l-4');
    expect(bars[1].className).not.toContain('border-r-4');
  });
});
