import { TruckListingRow } from '../trucks.models';
import { ALL, filterTrucks, paginateTrucks, sortTrucks } from './truck-table.filters';

const trucks: TruckListingRow[] = [
  {
    id: 't1',
    truckNumber: 'T2000',
    engineState: 'On',
    ecuSpeedMph: 0,
    currentDriverName: 'Zoe Adams',
    currentTrailerLabel: null,
  },
  {
    id: 't2',
    truckNumber: 'T1000',
    engineState: 'Off',
    ecuSpeedMph: null,
    currentDriverName: null,
    currentTrailerLabel: null,
  },
];

describe('filterTrucks', () => {
  it('matches by truck number or current driver name, case-insensitively', () => {
    expect(filterTrucks(trucks, { searchText: 't2000', status: ALL })).toEqual([trucks[0]]);
    expect(filterTrucks(trucks, { searchText: 'zoe', status: ALL })).toEqual([trucks[0]]);
  });

  it('filters by derived truck status', () => {
    expect(filterTrucks(trucks, { searchText: '', status: 'off' })).toEqual([trucks[1]]);
    expect(filterTrucks(trucks, { searchText: '', status: 'on' })).toEqual([trucks[0]]);
  });

  it('returns everything when filters are empty/ALL', () => {
    expect(filterTrucks(trucks, { searchText: '', status: ALL })).toEqual(trucks);
  });
});

describe('sortTrucks', () => {
  it('sorts by truck number ascending', () => {
    expect(sortTrucks(trucks, 'truckNumber-asc').map((t) => t.truckNumber)).toEqual(['T1000', 'T2000']);
  });

  it('sorts by truck number descending', () => {
    expect(sortTrucks(trucks, 'truckNumber-desc').map((t) => t.truckNumber)).toEqual(['T2000', 'T1000']);
  });
});

describe('paginateTrucks', () => {
  it('slices to the requested page', () => {
    expect(paginateTrucks(trucks, 1, 1)).toEqual([trucks[0]]);
    expect(paginateTrucks(trucks, 2, 1)).toEqual([trucks[1]]);
  });
});
