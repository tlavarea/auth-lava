import { DriverListingRow } from '../drivers.models';

export const ALL = 'all';

// Normalizes a nullable dutyStatus into a stable, non-null key - keeps null out of the hlm-select value binding and
// gives "no HOS data synced yet" its own filterable/sortable bucket instead of silently dropping those drivers.
export const UNKNOWN_DUTY_STATUS = 'unknown';

export function normalizedDutyStatus(dutyStatus: string | null): string {
  return dutyStatus ?? UNKNOWN_DUTY_STATUS;
}

export type SortOption = 'name-asc' | 'name-desc';

export const SORT_OPTION_LABELS: Record<SortOption, string> = {
  'name-asc': 'Name (A–Z)',
  'name-desc': 'Name (Z–A)',
};

export type DriverFilters = {
  searchText: string;
  dutyStatus: string;
};

export function filterDrivers(drivers: DriverListingRow[], filters: DriverFilters): DriverListingRow[] {
  const query = filters.searchText.trim().toLowerCase();

  return drivers.filter((driver) => {
    const matchesQuery =
      query === '' ||
      driver.name.toLowerCase().includes(query) ||
      (driver.currentVehicleName?.toLowerCase().includes(query) ?? false);
    const matchesStatus = filters.dutyStatus === ALL || normalizedDutyStatus(driver.dutyStatus) === filters.dutyStatus;
    return matchesQuery && matchesStatus;
  });
}

export function sortDrivers(drivers: DriverListingRow[], sortOption: SortOption): DriverListingRow[] {
  const sign = sortOption === 'name-asc' ? 1 : -1;
  return [...drivers].sort((a, b) => sign * a.name.localeCompare(b.name));
}

export function paginateDrivers(
  drivers: DriverListingRow[],
  currentPage: number,
  itemsPerPage: number
): DriverListingRow[] {
  const start = (currentPage - 1) * itemsPerPage;
  return drivers.slice(start, start + itemsPerPage);
}
