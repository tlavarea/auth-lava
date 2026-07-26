import { TrailerListingRow } from '../trailers.models';

export type SortOption = 'label-asc' | 'label-desc';

export const SORT_OPTION_LABELS: Record<SortOption, string> = {
  'label-asc': 'Label (A–Z)',
  'label-desc': 'Label (Z–A)',
};

export type TrailerFilters = {
  searchText: string;
};

export function filterTrailers(trailers: TrailerListingRow[], filters: TrailerFilters): TrailerListingRow[] {
  const query = filters.searchText.trim().toLowerCase();

  return trailers.filter((trailer) => {
    return (
      query === '' ||
      trailer.label.toLowerCase().includes(query) ||
      (trailer.manufacturer?.toLowerCase().includes(query) ?? false)
    );
  });
}

export function sortTrailers(trailers: TrailerListingRow[], sortOption: SortOption): TrailerListingRow[] {
  const sign = sortOption === 'label-asc' ? 1 : -1;
  return [...trailers].sort((a, b) => sign * a.label.localeCompare(b.label));
}

export function paginateTrailers(
  trailers: TrailerListingRow[],
  currentPage: number,
  itemsPerPage: number
): TrailerListingRow[] {
  const start = (currentPage - 1) * itemsPerPage;
  return trailers.slice(start, start + itemsPerPage);
}
