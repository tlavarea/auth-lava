import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DriverListingRow } from '../drivers.models';
import { DriverItem } from './driver-item';

describe('DriverItem', () => {
  let fixture: ComponentFixture<DriverItem>;

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverItem],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverItem);
    fixture.componentRef.setInput('drivers', drivers);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the whole row as a link to its detail route', () => {
    expect(fixture.nativeElement.textContent).toContain('Jane Doe');
    expect(fixture.nativeElement.textContent).toContain('Truck 7');

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/driver-42"]');
    expect(row).toBeTruthy();
    expect(row?.querySelector('button, a')).toBeFalsy();
  });

  it('shows a fallback when the driver has no current vehicle assignment', () => {
    fixture.componentRef.setInput('drivers', [{ ...drivers[0], currentVehicleName: null }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No vehicle assigned');
  });

  it('highlights the row matching selectedId and marks it aria-current', () => {
    fixture.componentRef.setInput('selectedId', 'driver-42');
    fixture.detectChanges();

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/driver-42"]');
    expect(row?.getAttribute('aria-current')).toBe('page');
    expect(row?.classList.contains('bg-accent')).toBe(true);
    expect(row?.classList.contains('hover:bg-muted/50')).toBe(false);
  });

  it('does not highlight any row when selectedId is null, and unselected rows get a hover class', () => {
    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/driver-42"]');
    expect(fixture.nativeElement.querySelector('[aria-current="page"]')).toBeFalsy();
    expect(row?.classList.contains('bg-accent')).toBe(false);
    expect(row?.classList.contains('hover:bg-muted/50')).toBe(true);
  });

  it('colors the driving status badge via the shared helper', () => {
    fixture.componentRef.setInput('drivers', [
      { ...drivers[0], id: 'd1', dutyStatus: 'driving' },
      { ...drivers[0], id: 'd2', dutyStatus: 'offDuty' },
    ]);
    fixture.detectChanges();

    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('a[data-slot="item"]'));
    expect(rows[0].querySelector('[hlmbadge]')?.getAttribute('data-variant')).toBe('success');
    expect(rows[1].querySelector('[hlmbadge]')?.getAttribute('data-variant')).toBe('secondary');
  });
});
