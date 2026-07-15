import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ShipmentDetailResponse } from '../shipments.models';
import { ShipmentsStore } from '../shipments.store';
import { ShipmentDetailPage } from './shipment-detail.page';

describe('ShipmentDetailPage', () => {
  let fixture: ComponentFixture<ShipmentDetailPage>;
  let httpMock: HttpTestingController;

  const detail: ShipmentDetailResponse = {
    listing: {
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
    totalAmount: 1000,
    lineHaulCost: 900,
    rateUsed: 1.5,
    scac: 'ABCD',
    scacName: 'Acme Carrier',
    tenderNumber: 'T-1',
    equipmentDesc: '53ft Van',
    requestorName: 'Jane Doe',
    requestorEmail: 'jane@example.com',
    rawResponse: '{}',
    syncedAt: '2026-07-14T00:00:00',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShipmentDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), ShipmentsStore],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ShipmentDetailPage);
    fixture.componentRef.setInput('id', '42');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders the shipment detail for the routed :id', async () => {
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('SHP-42');
    expect(fixture.nativeElement.textContent).toContain('ABCD');
  });

  it('clears the selected detail on destroy', async () => {
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    await fixture.whenStable();

    const store = TestBed.inject(ShipmentsStore);
    expect(store.selectedDetail()).toEqual(detail);

    fixture.destroy();

    expect(store.selectedDetail()).toBeNull();
  });
});
