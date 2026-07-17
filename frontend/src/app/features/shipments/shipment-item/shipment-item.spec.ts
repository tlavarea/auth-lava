import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ShipmentListingRow } from '../shipments.models';
import { ShipmentItem } from './shipment-item';

describe('ShipmentItem', () => {
  let fixture: ComponentFixture<ShipmentItem>;

  const shipments: ShipmentListingRow[] = [
    {
      offerId: 42,
      status: 'ACCEPTED',
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
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShipmentItem],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ShipmentItem);
    fixture.componentRef.setInput('shipments', shipments);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the whole row as a link to its detail route, titled by route state', () => {
    expect(fixture.nativeElement.textContent).toContain('NC');
    expect(fixture.nativeElement.textContent).toContain('WA');
    expect(fixture.nativeElement.textContent).toContain('Rank 15');
    expect(fixture.nativeElement.textContent).not.toContain('SHP-42');

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/42"]');
    expect(row).toBeTruthy();
    expect(row?.querySelector('button, a')).toBeFalsy();
  });

  it('shows exactly pickup date, required delivery, and equipment on the second line', () => {
    expect(fixture.nativeElement.textContent).toContain('Pickup 2026-08-01');
    expect(fixture.nativeElement.textContent).toContain('Required 2026-08-10');
    expect(fixture.nativeElement.textContent).toContain('53ft Van');
  });

  it('highlights the row matching selectedId and marks it aria-current', () => {
    fixture.componentRef.setInput('selectedId', 42);
    fixture.detectChanges();

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/42"]');
    expect(row?.getAttribute('aria-current')).toBe('page');
    expect(row?.classList.contains('bg-accent')).toBe(true);
    expect(row?.classList.contains('border-e-0')).toBe(true);
    expect(row?.classList.contains('w-[calc(100%+1rem)]')).toBe(true);
    expect(row?.classList.contains('hover:bg-muted/50')).toBe(false);
  });

  it('does not highlight any row when selectedId is null, and unselected rows get a hover class', () => {
    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/42"]');
    expect(fixture.nativeElement.querySelector('[aria-current="page"]')).toBeFalsy();
    expect(row?.classList.contains('bg-accent')).toBe(false);
    expect(row?.classList.contains('hover:bg-muted/50')).toBe(true);
  });

  it('shows an "On Route" badge only for shipments flagged as viable pickups', () => {
    fixture.componentRef.setInput('shipments', [
      { ...shipments[0], offerId: 1, viablePickup: false },
      { ...shipments[0], offerId: 2, viablePickup: true },
    ]);
    fixture.detectChanges();

    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('a[data-slot="item"]'));
    expect(rows[0].textContent).not.toContain('On Route');
    expect(rows[1].textContent).toContain('On Route');
  });

  it('colors the rank and status badges via the shared helpers', () => {
    fixture.componentRef.setInput('shipments', [
      { ...shipments[0], offerId: 1, rank: '15', status: 'Open' },
      { ...shipments[0], offerId: 2, rank: '85', status: 'Awaiting Award' },
    ]);
    fixture.detectChanges();

    const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('a[data-slot="item"]'));
    const firstBadges = Array.from(rows[0].querySelectorAll('[hlmbadge]'));
    const secondBadges = Array.from(rows[1].querySelectorAll('[hlmbadge]'));

    expect(firstBadges[0].getAttribute('data-variant')).toBe('success'); // rank 15
    expect(firstBadges[1].getAttribute('data-variant')).toBe('success'); // Open
    expect(secondBadges[0].getAttribute('data-variant')).toBe('destructive'); // rank 85
    expect(secondBadges[1].getAttribute('data-variant')).toBe('info'); // Awaiting Award
  });
});
