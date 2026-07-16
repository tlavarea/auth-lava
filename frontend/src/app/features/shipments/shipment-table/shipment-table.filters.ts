import { ShipmentListingRow } from '../shipments.models';

export const ALL = 'all';

export type SortOption =
  | 'pickupDate-asc'
  | 'pickupDate-desc'
  | 'requiredDeliveryDate-asc'
  | 'requiredDeliveryDate-desc'
  | 'rank-asc'
  | 'rank-desc';

export const SORT_OPTION_LABELS: Record<SortOption, string> = {
  'pickupDate-asc': 'Pickup date (soonest)',
  'pickupDate-desc': 'Pickup date (latest)',
  'requiredDeliveryDate-asc': 'Required delivery (soonest)',
  'requiredDeliveryDate-desc': 'Required delivery (latest)',
  'rank-asc': 'Rank (highest first)',
  'rank-desc': 'Rank (lowest first)',
};

export type ShipmentFilters = {
  searchText: string;
  status: string;
  equipType: string;
};

export function filterShipments(shipments: ShipmentListingRow[], filters: ShipmentFilters): ShipmentListingRow[] {
  const query = filters.searchText.trim().toLowerCase();

  return shipments.filter((shipment) => {
    const matchesQuery =
      query === '' ||
      shipment.shipmentId.toLowerCase().includes(query) ||
      shipment.origin.toLowerCase().includes(query) ||
      shipment.destination.toLowerCase().includes(query);
    const matchesStatus = filters.status === ALL || shipment.status === filters.status;
    const matchesEquipType = filters.equipType === ALL || shipment.equipType === filters.equipType;
    return matchesQuery && matchesStatus && matchesEquipType;
  });
}

export function sortShipments(shipments: ShipmentListingRow[], sortOption: SortOption): ShipmentListingRow[] {
  const [field, direction] = parseSortOption(sortOption);
  const sign = direction === 'asc' ? 1 : -1;

  return [...shipments].sort((a, b) => {
    if (field === 'rank') {
      return sign * (Number(a.rank) - Number(b.rank));
    }
    const aValue = a[field];
    const bValue = b[field];
    if (aValue === null && bValue === null) {
      return 0;
    }
    if (aValue === null) {
      return 1;
    }
    if (bValue === null) {
      return -1;
    }
    return sign * aValue.localeCompare(bValue);
  });
}

export function paginateShipments(
  shipments: ShipmentListingRow[],
  currentPage: number,
  itemsPerPage: number
): ShipmentListingRow[] {
  const start = (currentPage - 1) * itemsPerPage;
  return shipments.slice(start, start + itemsPerPage);
}

function parseSortOption(option: SortOption): ['pickupDate' | 'requiredDeliveryDate' | 'rank', 'asc' | 'desc'] {
  const separatorIndex = option.lastIndexOf('-');
  const field = option.slice(0, separatorIndex) as 'pickupDate' | 'requiredDeliveryDate' | 'rank';
  const direction = option.slice(separatorIndex + 1) as 'asc' | 'desc';
  return [field, direction];
}
