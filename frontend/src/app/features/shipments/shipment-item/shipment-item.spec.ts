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
      rank: 'E-5',
      gbloc: 'ABCD',
      origin: 'Fort Liberty, NC',
      destination: 'Joint Base Lewis-McChord, WA',
      equipType: '53ft Van',
      conveyancesOffered: 1,
      conveyancesAccepted: 1,
      pickupDate: '2026-08-01',
      requiredDeliveryDate: '2026-08-10',
      syncedAt: '2026-07-14T00:00:00',
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

  it('renders the whole row as a link to its detail route', () => {
    expect(fixture.nativeElement.textContent).toContain('SHP-42');
    expect(fixture.nativeElement.textContent).toContain('Fort Liberty, NC');

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/42"]');
    expect(row).toBeTruthy();
    expect(row?.querySelector('button, a')).toBeFalsy();
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
});
