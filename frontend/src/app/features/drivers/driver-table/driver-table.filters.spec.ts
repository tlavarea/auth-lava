import { DriverListingRow } from '../drivers.models';
import {
  filterDrivers,
  normalizedDutyStatus,
  paginateDrivers,
  sortDrivers,
  UNKNOWN_DUTY_STATUS,
} from './driver-table.filters';

function makeDriver(overrides: Partial<DriverListingRow>): DriverListingRow {
  return {
    id: 'driver-1',
    name: 'Jane Doe',
    activationStatus: 'active',
    currentVehicleName: 'Truck 7',
    dutyStatus: 'driving',
    currentLocation: 'Fort Worth, TX',
    ...overrides,
  };
}

const drivers: DriverListingRow[] = [
  makeDriver({ id: 'driver-42', name: 'Zoe Adams', currentVehicleName: 'Truck 7', dutyStatus: 'driving' }),
  makeDriver({ id: 'driver-43', name: 'Amir Khan', currentVehicleName: null, dutyStatus: 'offDuty' }),
];

describe('filterDrivers', () => {
  it('returns everything when no filters are active', () => {
    expect(filterDrivers(drivers, { searchText: '', dutyStatus: 'all' })).toHaveLength(2);
  });

  it('matches search text against name case-insensitively', () => {
    const result = filterDrivers(drivers, { searchText: 'amir', dutyStatus: 'all' });
    expect(result.map((d) => d.id)).toEqual(['driver-43']);
  });

  it('matches search text against currentVehicleName', () => {
    const result = filterDrivers(drivers, { searchText: 'truck 7', dutyStatus: 'all' });
    expect(result.map((d) => d.id)).toEqual(['driver-42']);
  });

  it('does not throw when currentVehicleName is null', () => {
    const result = filterDrivers(drivers, { searchText: 'nonexistent', dutyStatus: 'all' });
    expect(result).toEqual([]);
  });

  it('narrows by exact duty status', () => {
    const result = filterDrivers(drivers, { searchText: '', dutyStatus: 'offDuty' });
    expect(result.map((d) => d.id)).toEqual(['driver-43']);
  });

  it('combines filters with AND semantics', () => {
    const result = filterDrivers(drivers, { searchText: 'zoe', dutyStatus: 'offDuty' });
    expect(result).toHaveLength(0);
  });

  it('narrows by the unknown-duty-status sentinel when dutyStatus is null', () => {
    const noDutyStatus = [makeDriver({ id: 'driver-44', dutyStatus: null })];
    const result = filterDrivers(noDutyStatus, { searchText: '', dutyStatus: UNKNOWN_DUTY_STATUS });
    expect(result.map((d) => d.id)).toEqual(['driver-44']);
  });
});

describe('normalizedDutyStatus', () => {
  it('passes through a real duty status', () => {
    expect(normalizedDutyStatus('driving')).toBe('driving');
  });

  it('maps null to the unknown sentinel', () => {
    expect(normalizedDutyStatus(null)).toBe(UNKNOWN_DUTY_STATUS);
  });
});

describe('sortDrivers', () => {
  it('sorts by name ascending and descending', () => {
    expect(sortDrivers(drivers, 'name-asc').map((d) => d.id)).toEqual(['driver-43', 'driver-42']);
    expect(sortDrivers(drivers, 'name-desc').map((d) => d.id)).toEqual(['driver-42', 'driver-43']);
  });

  it('does not mutate the input array', () => {
    const original = [...drivers];
    sortDrivers(drivers, 'name-desc');
    expect(drivers).toEqual(original);
  });
});

describe('paginateDrivers', () => {
  it('slices by page and page size', () => {
    expect(paginateDrivers(drivers, 1, 1).map((d) => d.id)).toEqual(['driver-42']);
    expect(paginateDrivers(drivers, 2, 1).map((d) => d.id)).toEqual(['driver-43']);
  });

  it('returns an empty page past the end of the data', () => {
    expect(paginateDrivers(drivers, 3, 1)).toEqual([]);
  });
});
