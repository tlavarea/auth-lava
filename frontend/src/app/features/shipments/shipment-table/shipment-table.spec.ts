import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ShipmentListingRow } from '../shipments.models';
import { ShipmentTable } from './shipment-table';

describe('ShipmentTable', () => {
  let fixture: ComponentFixture<ShipmentTable>;

  const shipments: ShipmentListingRow[] = [
    {
      offerId: 42,
      status: 'Open',
      expirationDate: null,
      shipmentId: 'SHP-42',
      shipmentType: 'HHG',
      rank: '15',
      gbloc: 'ABCD',
      origin: 'Fort Liberty, NC',
      destination: 'Joint Base Lewis-McChord, WA',
      equipType: '53ft Van',
      conveyancesOffered: 1,
      conveyancesAccepted: 1,
      pickupDate: '2026-08-01',
      requiredDeliveryDate: '2026-08-10',
      syncedAt: '2026-07-14T00:00:00',
      viablePickup: false,
    },
    {
      offerId: 43,
      status: 'Awaiting Award',
      expirationDate: null,
      shipmentId: 'SHP-43',
      shipmentType: 'HHG',
      rank: '85',
      gbloc: 'WXYZ',
      origin: 'Fort Campbell, KY',
      destination: 'Fort Bragg, NC',
      equipType: '48ft Van',
      conveyancesOffered: 2,
      conveyancesAccepted: 1,
      pickupDate: '2026-07-20',
      requiredDeliveryDate: null,
      syncedAt: '2026-07-14T00:00:00',
      viablePickup: true,
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
      imports: [ShipmentTable],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ShipmentTable);
    fixture.componentRef.setInput('shipments', shipments);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a card per shipment titled by route state, with rank and status badges', () => {
    expect(cards().length).toBe(2);

    // default sort is rank ascending: SHP-42 (rank 15) before SHP-43 (rank 85)
    const [firstCard, secondCard] = cards();

    expect(firstCard.textContent).toContain('WA');
    expect(firstCard.textContent).not.toContain('SHP-42');

    const firstBadges = Array.from(firstCard.querySelectorAll('[hlmbadge]'));
    expect(firstBadges[0].textContent?.trim()).toBe('15');
    expect(firstBadges[0].getAttribute('data-variant')).toBe('success');
    expect(firstBadges[1].textContent?.trim()).toBe('Open');
    expect(firstBadges[1].getAttribute('data-variant')).toBe('success');

    expect(secondCard.textContent).toContain('KY');
    expect(secondCard.textContent).not.toContain('SHP-43');

    const secondBadges = Array.from(secondCard.querySelectorAll('[hlmbadge]'));
    expect(secondBadges[0].textContent?.trim()).toBe('85');
    expect(secondBadges[0].getAttribute('data-variant')).toBe('destructive');
    expect(secondBadges[1].textContent?.trim()).toBe('Awaiting Award');
    expect(secondBadges[1].getAttribute('data-variant')).toBe('info');
  });

  it('shows an "On Route" badge only for shipments flagged as viable pickups', () => {
    const [firstCard, secondCard] = cards();

    expect(firstCard.textContent).not.toContain('On Route');
    expect(secondCard.textContent).toContain('On Route');
  });

  it('shows exactly pickup date, required delivery, and equipment in the second row', () => {
    const [firstCard] = cards();
    expect(firstCard.textContent).toContain('Pickup');
    expect(firstCard.textContent).toContain('2026-08-01');
    expect(firstCard.textContent).toContain('Required delivery');
    expect(firstCard.textContent).toContain('2026-08-10');
    expect(firstCard.textContent).toContain('Equipment');
    expect(firstCard.textContent).toContain('53ft Van');
    expect(firstCard.textContent).not.toContain('Conveyances');
  });

  it('narrows results by search text and shows a removable filter chip', () => {
    typeSearch('Campbell');

    expect(cards().length).toBe(1);
    expect(cards()[0].textContent).not.toContain('WA');

    const removeChip: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      'button[aria-label^="Remove filter"]'
    );
    expect(removeChip).toBeTruthy();

    removeChip?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });

  it('shows an empty state with a clear-filters action when no shipment matches', () => {
    typeSearch('no such shipment');

    expect(cards().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('No shipments match your filters');

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const clearFilters = buttons.find((button) => button.textContent?.trim() === 'Clear filters');
    expect(clearFilters).toBeTruthy();

    clearFilters?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });
});
