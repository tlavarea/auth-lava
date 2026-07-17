import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverActivityEntry } from '../drivers.models';
import { HosDutyStatusTimeline } from './hos-duty-status-timeline';

// Builds a real UTC instant (as the backend now sends - see DriverActivityEntry's model comment) from a given local
// wall-clock time on Jul 16, 2026, so the expected totals below hold on any CI runner's timezone and regression-test
// the fix for a bug where the backend previously dropped the UTC offset instead of preserving it.
function localIso(hour: number, minute: number): string {
  return new Date(2026, 6, 16, hour, minute, 0).toISOString();
}

describe('HosDutyStatusTimeline', () => {
  let fixture: ComponentFixture<HosDutyStatusTimeline>;

  const entries: DriverActivityEntry[] = [
    {
      dutyStatus: 'driving',
      startTime: localIso(9, 0),
      endTime: null,
      latitude: null,
      longitude: null,
      remark: null,
    },
    {
      dutyStatus: 'onDuty',
      startTime: localIso(8, 30),
      endTime: localIso(9, 0),
      latitude: null,
      longitude: null,
      remark: 'Pre-trip inspection',
    },
    {
      dutyStatus: 'offDuty',
      startTime: localIso(0, 0),
      endTime: localIso(8, 30),
      latitude: null,
      longitude: null,
      remark: null,
    },
  ];

  beforeEach(async () => {
    vi.setSystemTime(new Date(2026, 6, 16, 10, 0, 0)); // Jul 16, 2026, 10:00 AM local time

    await TestBed.configureTestingModule({
      imports: [HosDutyStatusTimeline],
    }).compileComponents();

    fixture = TestBed.createComponent(HosDutyStatusTimeline);
    fixture.componentRef.setInput('entries', entries);
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('draws one continuous step-line path across the OFF -> ON -> D segments', () => {
    const path: SVGPathElement = fixture.nativeElement.querySelector('path');
    const d = path.getAttribute('d') ?? '';

    expect(d.startsWith('M')).toBe(true);
    // Contiguous segments (each endTime === the next startTime) draw as a single unbroken path with no extra
    // "M" moveto commands beyond the initial one.
    expect(d.match(/M/g)?.length).toBe(1);
  });

  it('sums each row total and clips the still-open driving segment to "now"', () => {
    const text = fixture.nativeElement.textContent;

    // offDuty: 00:00-08:30 = 8:30:00; onDuty: 08:30-09:00 = 0:30:00; driving: 09:00-"now" (10:00) = 1:00:00.
    expect(text).toContain('8:30:00');
    expect(text).toContain('0:30:00');
    expect(text).toContain('1:00:00');
    expect(text).toContain('Total');
    expect(text).toContain('10:00:00');
  });

  it('renders row labels for all 4 FMCSA duty-status rows', () => {
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('OFF');
    expect(text).toContain('SB');
    expect(text).toContain('D');
    expect(text).toContain('ON');
  });

  it('exposes per-row totals as text via aria-label rather than color alone', () => {
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    expect(svg.getAttribute('aria-label')).toContain('OFF 8:30:00');
    expect(svg.getAttribute('aria-label')).toContain('D 1:00:00');
  });

  it('gives each segment a native hover tooltip with its status, duration, and remark', () => {
    const rects: SVGRectElement[] = Array.from(fixture.nativeElement.querySelectorAll('rect'));
    const titles = rects.map((rect) => rect.querySelector('title')?.textContent);

    expect(titles).toContain('On Duty: 0:30:00 — Pre-trip inspection');
    expect(titles).toContain('Driving: 1:00:00');
  });

  it('renders no line and zero totals when there is no activity yet today', () => {
    fixture.componentRef.setInput('entries', []);
    fixture.detectChanges();

    const path: SVGPathElement = fixture.nativeElement.querySelector('path');
    expect(path.getAttribute('d')).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Total');
    expect(fixture.nativeElement.textContent).toContain('0:00:00');
  });
});
