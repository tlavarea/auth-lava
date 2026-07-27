import { TruckStatus, truckStatus } from '../truck-status';
import { TruckListingRow } from '../trucks.models';

export const ALL = 'all';

export type SortOption = 'truckNumber-asc' | 'truckNumber-desc';

export const SORT_OPTION_LABELS: Record<SortOption, string> = {
  'truckNumber-asc': 'Truck # (A–Z)',
  'truckNumber-desc': 'Truck # (Z–A)',
};

export type TruckFilters = {
  searchText: string;
  status: TruckStatus | typeof ALL;
};

export function filterTrucks(trucks: TruckListingRow[], filters: TruckFilters): TruckListingRow[] {
  const query = filters.searchText.trim().toLowerCase();

  return trucks.filter((truck) => {
    const matchesQuery =
      query === '' ||
      truck.truckNumber.toLowerCase().includes(query) ||
      (truck.currentDriverName?.toLowerCase().includes(query) ?? false);
    const matchesStatus =
      filters.status === ALL || truckStatus(truck.engineState, truck.ecuSpeedMph) === filters.status;
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
