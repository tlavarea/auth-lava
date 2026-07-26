import { TruckListingRow } from '../trucks.models';

export const ALL = 'all';

// Normalizes a nullable statusCode into a stable, non-null key - keeps null out of the hlm-select value binding.
// Vektor's statusCode is a raw, unconfirmed integer (only 1/3 ever observed - see backend's VektorTruckRow javadoc),
// so it's filtered/labeled as-is rather than mapped to a semantic "Active"/"Inactive" meaning.
export const UNKNOWN_STATUS_CODE = 'unknown';

export function normalizedStatusCode(statusCode: number | null): string {
  return statusCode === null ? UNKNOWN_STATUS_CODE : String(statusCode);
}

export function statusCodeLabel(statusCode: string): string {
  return statusCode === UNKNOWN_STATUS_CODE ? 'Unknown' : `Status ${statusCode}`;
}

export type SortOption = 'truckNumber-asc' | 'truckNumber-desc';

export const SORT_OPTION_LABELS: Record<SortOption, string> = {
  'truckNumber-asc': 'Truck # (A–Z)',
  'truckNumber-desc': 'Truck # (Z–A)',
};

export type TruckFilters = {
  searchText: string;
  statusCode: string;
};

export function filterTrucks(trucks: TruckListingRow[], filters: TruckFilters): TruckListingRow[] {
  const query = filters.searchText.trim().toLowerCase();

  return trucks.filter((truck) => {
    const matchesQuery =
      query === '' ||
      truck.truckNumber.toLowerCase().includes(query) ||
      (truck.currentDriverName?.toLowerCase().includes(query) ?? false);
    const matchesStatus = filters.statusCode === ALL || normalizedStatusCode(truck.statusCode) === filters.statusCode;
    return matchesQuery && matchesStatus;
  });
}

export function sortTrucks(trucks: TruckListingRow[], sortOption: SortOption): TruckListingRow[] {
  const sign = sortOption === 'truckNumber-asc' ? 1 : -1;
  return [...trucks].sort((a, b) => sign * a.truckNumber.localeCompare(b.truckNumber));
}

export function paginateTrucks(
  trucks: TruckListingRow[],
  currentPage: number,
  itemsPerPage: number
): TruckListingRow[] {
  const start = (currentPage - 1) * itemsPerPage;
  return trucks.slice(start, start + itemsPerPage);
}
