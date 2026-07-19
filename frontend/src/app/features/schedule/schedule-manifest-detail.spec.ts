import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ScheduleManifestDetail } from './schedule-manifest-detail';
import { ManifestEta, ManifestRoute, ManifestStop } from './schedule.models';

describe('ScheduleManifestDetail', () => {
  let fixture: ComponentFixture<ScheduleManifestDetail>;

  const pickup: ManifestStop = {
    stopId: 'stop-uuid-1',
    sequenceNumber: 1,
    stopType: 'PICKUP',
    siteName: 'Dealer Warehouse',
    address: '4251 Turin Dr, Bessemer, AL 35020',
    latitude: 33.101,
    longitude: -87.99,
    timezoneAbbreviation: 'CDT',
    appointmentWindowStart: '2026-07-17T08:00:00',
    appointmentWindowEnd: '2026-07-17T10:00:00',
    arrivedAt: null,
    checkedInAt: null,
    checkedOutAt: '2026-07-17T09:15:00',
    referenceNumbers: 'CO 01660967',
    notes: null,
    contactPhone: null,
    estimatedMilesToNext: 1800,
    actualMilesToNext: null,
    odometerMiles: 406717,
  };

  const dropoff: ManifestStop = {
    stopId: 'stop-uuid-2',
    sequenceNumber: 2,
    stopType: 'DROPOFF',
    siteName: 'Alsup Facility',
    address: '6390 N Alsup Rd, Litchfield Park, AZ 85340',
    latitude: 33.489,
    longitude: -112.361,
    timezoneAbbreviation: 'CDT',
    appointmentWindowStart: '2026-07-20T08:00:00',
    appointmentWindowEnd: '2026-07-20T10:00:00',
    arrivedAt: null,
    checkedInAt: null,
    checkedOutAt: null,
    referenceNumbers: 'CO 01660967',
    notes: null,
    contactPhone: null,
    estimatedMilesToNext: null,
    actualMilesToNext: null,
    odometerMiles: null,
  };

  const route: ManifestRoute = {
    stops: [pickup, dropoff],
    startingPosition: null,
    encodedPolyline: 'abc123',
    distanceMeters: 1_800_000,
    duration: '64800s',
  };

  async function render(routeValue: ManifestRoute | null, etaValue: ManifestEta | null): Promise<void> {
    await TestBed.configureTestingModule({ imports: [ScheduleManifestDetail] }).compileComponents();
    fixture = TestBed.createComponent(ScheduleManifestDetail);
    fixture.componentRef.setInput('route', routeValue);
    fixture.componentRef.setInput('eta', etaValue);
    fixture.detectChanges();
  }

  it('renders no eta block for any stop when eta is null', async () => {
    await render(route, null);

    expect(fixture.nativeElement.textContent).not.toContain('ETA');
    expect(fixture.nativeElement.textContent).not.toContain('mi remaining');
  });

  // Early - the estimated arrival lands before the target stop's appointment window opens (mirrors the confirmed
  // Michael Goodson example from Vektor's own UI: 552.86mi/9h out, ETA a day before an 8am appointment).
  it('renders the eta block on the stop matching stopSequenceNumber, with an early comparison', async () => {
    const eta: ManifestEta = {
      stopSequenceNumber: 2,
      remainingMiles: 552.8629675570663,
      remainingMinutes: 567,
      estimatedArrival: '2026-07-19T02:16:00',
    };
    await render(route, eta);

    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.space-y-1\\.5'));
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).not.toContain('ETA');
    expect(rows[1].textContent).toContain('ETA');
    expect(rows[1].textContent).toContain('552.9 mi remaining');
    expect(rows[1].textContent).toContain('Early +1d 5h');
  });

  it('renders a late comparison when the estimated arrival is after the appointment window closes', async () => {
    const eta: ManifestEta = {
      stopSequenceNumber: 2,
      remainingMiles: 40,
      remainingMinutes: 50,
      estimatedArrival: '2026-07-20T10:45:00',
    };
    await render(route, eta);

    expect(fixture.nativeElement.textContent).toContain('Late +45m');
  });

  it('renders "On time" when the estimated arrival falls within the appointment window', async () => {
    const eta: ManifestEta = {
      stopSequenceNumber: 2,
      remainingMiles: 12,
      remainingMinutes: 15,
      estimatedArrival: '2026-07-20T09:00:00',
    };
    await render(route, eta);

    expect(fixture.nativeElement.textContent).toContain('On time');
  });

  it('renders the stop status badges derived from arrival/check-in/check-out timestamps', async () => {
    await render(route, null);

    expect(fixture.nativeElement.textContent).toContain('Completed');
    expect(fixture.nativeElement.textContent).toContain('En Route');
  });

  it('renders nothing when there is no route yet', async () => {
    await render(null, null);

    expect(fixture.nativeElement.querySelectorAll('.space-y-1\\.5')).toHaveLength(0);
  });
});
