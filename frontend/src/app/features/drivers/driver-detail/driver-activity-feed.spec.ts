import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DriverActivityEntry } from '../drivers.models';
import { DriverActivityFeed } from './driver-activity-feed';

// Builds a real UTC instant (as the backend now sends - see DriverActivityEntry's model comment) from a given local
// wall-clock time on Jul 16, 2026, so the expected formatted-time assertions below hold on any CI runner's timezone.
function localIso(hour: number, minute: number): string {
  return new Date(2026, 6, 16, hour, minute, 0).toISOString();
}

describe('DriverActivityFeed', () => {
  let fixture: ComponentFixture<DriverActivityFeed>;

  const entries: DriverActivityEntry[] = [
    {
      dutyStatus: 'driving',
      startTime: localIso(11, 4),
      endTime: null,
      latitude: 27.9,
      longitude: -81.6,
      remark: null,
    },
    {
      dutyStatus: 'onDuty',
      startTime: localIso(10, 48),
      endTime: localIso(11, 4),
      latitude: null,
      longitude: null,
      remark: 'Pre-trip inspection',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverActivityFeed],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverActivityFeed);
    fixture.componentRef.setInput('entries', entries);
    fixture.componentRef.setInput('asOf', localIso(14, 25));
    fixture.componentRef.setInput('currentLocation', 'US 27, Lake Wales, FL, 33859');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders each entry with its status label and a time-of-day-only timestamp', () => {
    const items: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('li'));
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Driving');
    expect(items[0].textContent).toContain('11:04 AM');
    expect(items[1].textContent).toContain('On Duty');
    expect(items[1].textContent).toContain('Pre-trip inspection');
    expect(items[1].textContent).toContain('10:48 AM');
  });

  it('colors the status badge via the shared helper', () => {
    const badges: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('[hlmbadge]'));
    expect(badges[0].getAttribute('data-variant')).toBe('success');
    expect(badges[1].getAttribute('data-variant')).toBe('warning');
  });

  it('renders the "Today" header with the formatted as-of date/time', () => {
    expect(fixture.nativeElement.textContent).toContain('Today • Jul 16, 2026 2:25 PM');
  });

  it('renders the current location when present', () => {
    expect(fixture.nativeElement.textContent).toContain('US 27, Lake Wales, FL, 33859');
  });

  it('omits the location line when there is no current location', () => {
    fixture.componentRef.setInput('currentLocation', null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('ng-icon[name="lucideMapPin"]')).toBeFalsy();
  });

  it('shows a fallback message when there is no activity', () => {
    fixture.componentRef.setInput('entries', []);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No activity today.');
    expect(fixture.nativeElement.querySelectorAll('li[class*="items-start"]').length).toBe(0);
  });
});
