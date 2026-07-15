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

  it('renders a card per shipment with a link to its detail route', () => {
    expect(fixture.nativeElement.textContent).toContain('SHP-42');
    expect(fixture.nativeElement.textContent).toContain('Fort Liberty, NC');

    const detailLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/42"]');
    expect(detailLink?.textContent).toContain('View details');
  });
});
