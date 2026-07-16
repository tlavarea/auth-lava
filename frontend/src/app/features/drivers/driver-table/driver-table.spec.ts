import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DriverListingRow } from '../drivers.models';
import { DriverTable } from './driver-table';

describe('DriverTable', () => {
  let fixture: ComponentFixture<DriverTable>;

  const drivers: DriverListingRow[] = [
    {
      id: 'driver-42',
      name: 'Zoe Adams',
      activationStatus: 'active',
      currentVehicleName: 'Truck 7',
      dutyStatus: 'driving',
      currentLocation: 'Fort Worth, TX',
    },
    {
      id: 'driver-43',
      name: 'Amir Khan',
      activationStatus: 'active',
      currentVehicleName: null,
      dutyStatus: 'offDuty',
      currentLocation: null,
    },
  ];

  function searchInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[hlmInputGroupInput]');
  }

  function cards(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('a[data-slot="item"]'));
  }

  function typeSearch(value: string): void {
    const input = searchInput();
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DriverTable],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DriverTable);
    fixture.componentRef.setInput('drivers', drivers);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a card per driver, name-sorted ascending by default, with a driving status badge', () => {
    expect(cards().length).toBe(2);

    const [firstCard, secondCard] = cards();
    expect(firstCard.textContent).toContain('Amir Khan');
    expect(secondCard.textContent).toContain('Zoe Adams');

    const firstBadge = firstCard.querySelector('[hlmbadge]');
    expect(firstBadge?.textContent?.trim()).toBe('Off Duty');
    expect(firstBadge?.getAttribute('data-variant')).toBe('secondary');
  });

  it('shows current vehicle/location or a fallback', () => {
    const [firstCard, secondCard] = cards();
    expect(firstCard.textContent).toContain('—');
    expect(secondCard.textContent).toContain('Truck 7');
    expect(secondCard.textContent).toContain('Fort Worth, TX');
  });

  it('narrows results by search text and shows a removable filter chip', () => {
    typeSearch('Zoe');

    expect(cards().length).toBe(1);
    expect(cards()[0].textContent).toContain('Zoe Adams');

    const removeChip: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      'button[aria-label^="Remove filter"]'
    );
    expect(removeChip).toBeTruthy();

    removeChip?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });

  it('shows an empty state with a clear-filters action when no driver matches', () => {
    typeSearch('no such driver');

    expect(cards().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('No drivers match your filters');

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const clearFilters = buttons.find((button) => button.textContent?.trim() === 'Clear filters');
    expect(clearFilters).toBeTruthy();

    clearFilters?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });
});
