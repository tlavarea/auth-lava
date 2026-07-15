import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ShipmentListingRow } from '../shipments.models';
import { ShipmentTable } from './shipment-table';

describe('ShipmentTable', () => {
  let fixture: ComponentFixture<ShipmentTable>;

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

  it('renders a row per shipment', () => {
    expect(fixture.nativeElement.textContent).toContain('SHP-42');
    expect(fixture.nativeElement.textContent).toContain('Fort Liberty, NC');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });
});
