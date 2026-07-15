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
    bidDetail: {
      bidRank: 18,
      requestorPhone: '6195568965',
      originAddress: 'SPLC 889000000\nUSS CARL VINSON - NASNI PIER LIMA',
      destinationAddress: 'SPLC 879544000\nVFA-122 NAS LEMOORE',
      earliestPickupDisplay: '07/17/2026 08:00 AM',
      latestPickupDisplay: '07/17/2026 03:00 PM',
      latestDeliveryDisplay: '07/20/2026 12:00 PM',
      offerExpirationDisplay: 'Expired',
      quantity: 30000,
      quantityUom: 'LB',
      commodityCode: '999912',
      ratedCommodityCode: '999913',
      numberOfConveyances: 1,
      shipmentMode: 'Truckload',
      remarks: 'REQUESTING TRUCK AT NASNI PIER LIMA NLT 0800 ON 17JUL2026',
      sdg3Remarks: null,
      contractNumber: null,
      carrierPhone: '4802692601',
      tenderEffectiveDate: '2026-04-09',
      tenderExpirationDate: '2028-04-07',
      ratedMiles: 319,
      rateQualifier: 'PM',
      ratedQuantityLimits: null,
      serviceCost: 135.0,
      miscCost: 0,
      fuelAdjustment: 122.07,
      rins: '105,111,120,123,131,141,332,351',
      shipperRequestedServices: [
        { description: 'Protective Tarping', code: 'PTS', cost: 100.0, params: [] },
        { description: null, code: '405', cost: 122.07, params: [] },
      ],
      equipmentUnits: [
        {
          ciic: 'U',
          commodityCode: '999912',
          commodityDesc: 'FAK (See MFTRP 1C for Cargo Codes)',
          nsn: null,
          quantity: 30000,
          quantityUom: 'LB',
          items: [
            {
              description: 'SQUADRON GEAR',
              packType: 'MX',
              pieces: 1,
              quantity: 30000,
              quantityUom: 'LB',
              length: 0,
              width: 0,
              height: 0,
              cubicFeet: 3300,
            },
          ],
        },
      ],
    },
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

  it('renders bidDetail fields not covered by the original 9 typed columns', async () => {
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('6195568965');
    expect(text).toContain('USS CARL VINSON - NASNI PIER LIMA');
    expect(text).toContain('07/17/2026 08:00 AM');
    expect(text).toContain('Truckload');
    expect(text).toContain('319');
  });

  it('renders the Shipper Requested Services, Shipment Details, and RINs accordion sections', async () => {
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const text: string = fixture.nativeElement.textContent;
    expect(text).toContain('Shipper Requested Services');
    expect(text).toContain('Protective Tarping (PTS)');
    expect(text).toContain('(405)');
    expect(text).toContain('Shipment Details');
    expect(text).toContain('SQUADRON GEAR');
    expect(text).toContain('RINs');
    expect(text).toContain('105,111,120,123,131,141,332,351');
  });

  it('disables offer response submission until a response is chosen', async () => {
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const submitButton: HTMLButtonElement | undefined = buttons.find((button) =>
      button.textContent?.includes('Submit')
    );

    expect(submitButton?.disabled).toBe(true);
  });

  it('renders a labeled mobile back link and an icon-only desktop close link', () => {
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);

    const backLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.lg\\:hidden');
    const closeLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.hidden.lg\\:inline-flex');

    expect(backLink?.textContent).toContain('Back to shipments');
    expect(backLink?.querySelector('ng-icon')?.getAttribute('name')).toBe('lucideMoveLeft');
    expect(closeLink?.getAttribute('aria-label')).toBe('Back to shipments');
    expect(closeLink?.querySelector('ng-icon')?.getAttribute('name')).toBe('lucideX');
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
