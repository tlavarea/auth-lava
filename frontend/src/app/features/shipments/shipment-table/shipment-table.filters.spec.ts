import { ShipmentListingRow } from '../shipments.models';
import { filterShipments, paginateShipments, sortShipments } from './shipment-table.filters';

function makeShipment(overrides: Partial<ShipmentListingRow>): ShipmentListingRow {
  return {
    offerId: 1,
    status: 'Open',
    expirationDate: null,
    shipmentId: 'SHP-1',
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
    ...overrides,
  };
}

const shipments: ShipmentListingRow[] = [
  makeShipment({
    offerId: 42,
    status: 'Open',
    shipmentId: 'SHP-42',
    rank: '10',
    pickupDate: '2026-08-01',
    requiredDeliveryDate: '2026-08-10',
  }),
  makeShipment({
    offerId: 43,
    status: 'Awaiting Award',
    shipmentId: 'SHP-43',
    rank: '9',
    origin: 'Fort Campbell, KY',
    destination: 'Fort Bragg, NC',
    equipType: '48ft Van',
    pickupDate: '2026-07-20',
    requiredDeliveryDate: null,
  }),
];

describe('filterShipments', () => {
  it('returns everything when no filters are active', () => {
    expect(filterShipments(shipments, { searchText: '', status: 'all', equipType: 'all' })).toHaveLength(2);
  });

  it('matches search text against shipmentId, origin, and destination case-insensitively', () => {
    const result = filterShipments(shipments, { searchText: 'campbell', status: 'all', equipType: 'all' });
    expect(result.map((s) => s.shipmentId)).toEqual(['SHP-43']);
  });

  it('narrows by exact status', () => {
    const result = filterShipments(shipments, { searchText: '', status: 'Awaiting Award', equipType: 'all' });
    expect(result.map((s) => s.shipmentId)).toEqual(['SHP-43']);
  });

  it('narrows by exact equipment type', () => {
    const result = filterShipments(shipments, { searchText: '', status: 'all', equipType: '48ft Van' });
    expect(result.map((s) => s.shipmentId)).toEqual(['SHP-43']);
  });

  it('combines filters with AND semantics', () => {
    const result = filterShipments(shipments, {
      searchText: 'SHP',
      status: 'Open',
      equipType: '48ft Van',
    });
    expect(result).toHaveLength(0);
  });
});

describe('sortShipments', () => {
  it('sorts by rank ascending and descending, numerically not lexicographically', () => {
    // rank '9' vs '10': a lexicographic sort would wrongly put '10' before '9'
    expect(sortShipments(shipments, 'rank-asc').map((s) => s.shipmentId)).toEqual(['SHP-43', 'SHP-42']);
    expect(sortShipments(shipments, 'rank-desc').map((s) => s.shipmentId)).toEqual(['SHP-42', 'SHP-43']);
  });

  it('sorts by pickupDate ascending', () => {
    expect(sortShipments(shipments, 'pickupDate-asc').map((s) => s.shipmentId)).toEqual(['SHP-43', 'SHP-42']);
  });

  it('sorts null dates last regardless of direction', () => {
    expect(sortShipments(shipments, 'requiredDeliveryDate-asc').map((s) => s.shipmentId)).toEqual(['SHP-42', 'SHP-43']);
    expect(sortShipments(shipments, 'requiredDeliveryDate-desc').map((s) => s.shipmentId)).toEqual([
      'SHP-42',
      'SHP-43',
    ]);
  });

  it('does not mutate the input array', () => {
    const original = [...shipments];
    sortShipments(shipments, 'rank-desc');
    expect(shipments).toEqual(original);
  });
});

describe('paginateShipments', () => {
  it('slices by page and page size', () => {
    expect(paginateShipments(shipments, 1, 1).map((s) => s.shipmentId)).toEqual(['SHP-42']);
    expect(paginateShipments(shipments, 2, 1).map((s) => s.shipmentId)).toEqual(['SHP-43']);
  });

  it('returns an empty page past the end of the data', () => {
    expect(paginateShipments(shipments, 3, 1)).toEqual([]);
  });
});
