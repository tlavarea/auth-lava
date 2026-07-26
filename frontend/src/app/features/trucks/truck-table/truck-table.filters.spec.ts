import { TruckListingRow } from '../trucks.models';
import {
  ALL,
  filterTrucks,
  normalizedStatusCode,
  paginateTrucks,
  sortTrucks,
  statusCodeLabel,
  UNKNOWN_STATUS_CODE,
} from './truck-table.filters';

const trucks: TruckListingRow[] = [
  { id: 't1', truckNumber: 'T2000', statusCode: 1, currentDriverName: 'Zoe Adams', currentTrailerLabel: null },
  { id: 't2', truckNumber: 'T1000', statusCode: 3, currentDriverName: null, currentTrailerLabel: null },
];

describe('normalizedStatusCode', () => {
  it('returns the unknown sentinel for null', () => {
    expect(normalizedStatusCode(null)).toBe(UNKNOWN_STATUS_CODE);
  });

  it('stringifies a non-null code', () => {
    expect(normalizedStatusCode(1)).toBe('1');
  });
});

describe('statusCodeLabel', () => {
  it('labels the unknown sentinel as Unknown', () => {
    expect(statusCodeLabel(UNKNOWN_STATUS_CODE)).toBe('Unknown');
  });

  it('labels a raw code as "Status N"', () => {
    expect(statusCodeLabel('1')).toBe('Status 1');
  });
});

describe('filterTrucks', () => {
  it('matches by truck number or current driver name, case-insensitively', () => {
    expect(filterTrucks(trucks, { searchText: 't2000', statusCode: ALL })).toEqual([trucks[0]]);
    expect(filterTrucks(trucks, { searchText: 'zoe', statusCode: ALL })).toEqual([trucks[0]]);
  });

  it('filters by normalized status code', () => {
    expect(filterTrucks(trucks, { searchText: '', statusCode: '3' })).toEqual([trucks[1]]);
  });

  it('returns everything when filters are empty/ALL', () => {
    expect(filterTrucks(trucks, { searchText: '', statusCode: ALL })).toEqual(trucks);
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
