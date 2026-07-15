import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ShipmentDetailResponse, ShipmentListingRow } from './shipments.models';
import { ShipmentsStore } from './shipments.store';

describe('ShipmentsStore', () => {
  let store: InstanceType<typeof ShipmentsStore>;
  let httpMock: HttpTestingController;

  const listing: ShipmentListingRow = {
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
  };

  const detail: ShipmentDetailResponse = {
    listing,
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
    bidDetail: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ShipmentsStore],
    });
    store = TestBed.inject(ShipmentsStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts empty and idle', () => {
    expect(store.shipments()).toEqual([]);
    expect(store.listStatus()).toBe('idle');
    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });

  it('loadShipments() populates the list on success', async () => {
    const loadPromise = store.loadShipments();
    httpMock.expectOne('/api/sw-expedited/shipments').flush([listing]);
    await loadPromise;

    expect(store.shipments()).toEqual([listing]);
    expect(store.listStatus()).toBe('idle');
  });

  it('loadShipments() marks the list status as error on failure', async () => {
    const loadPromise = store.loadShipments();
    httpMock.expectOne('/api/sw-expedited/shipments').flush(null, { status: 500, statusText: 'Server Error' });
    await loadPromise;

    expect(store.listStatus()).toBe('error');
  });

  it('loadShipmentDetail() populates the selected detail on success', async () => {
    const loadPromise = store.loadShipmentDetail(42);
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    await loadPromise;

    expect(store.selectedDetail()).toEqual(detail);
    expect(store.detailStatus()).toBe('idle');
  });

  it('clearSelectedDetail() resets the selected detail', async () => {
    const loadPromise = store.loadShipmentDetail(42);
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    await loadPromise;

    store.clearSelectedDetail();

    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });

  it('respondToOffer() posts the response and resolves on success', async () => {
    const responsePromise = store.respondToOffer(42, { response: 'ACCEPT', conveyancesAvailable: 1 });
    const req = httpMock.expectOne('/api/sw-expedited/shipments/42/respond');
    expect(req.request.body).toEqual({ response: 'ACCEPT', conveyancesAvailable: 1 });
    req.flush(null);
    await responsePromise;

    expect(store.respondStatus()).toBe('idle');
  });

  it('respondToOffer() marks respondStatus as error and rethrows on failure', async () => {
    const responsePromise = store.respondToOffer(42, { response: 'DECLINE', conveyancesAvailable: 0 });
    httpMock
      .expectOne('/api/sw-expedited/shipments/42/respond')
      .flush(null, { status: 501, statusText: 'Not Implemented' });

    await expect(responsePromise).rejects.toBeTruthy();
    expect(store.respondStatus()).toBe('error');
  });
});
