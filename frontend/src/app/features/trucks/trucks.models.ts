// statusCode is Vektor's raw, unconfirmed status integer (only 1/3 ever observed) - rendered as-is rather than
// mapped to a semantic label. currentDriverName/currentTrailerLabel are resolved from vektor_truck's own
// current_driver_id/current_trailer_id, null when unassigned or when the referenced driver/trailer isn't currently
// synced - see the backend's TruckServiceImpl javadoc.
export type TruckListingRow = {
  id: string;
  truckNumber: string;
  statusCode: number | null;
  currentDriverName: string | null;
  currentTrailerLabel: string | null;
};

// Placeholder detail response - currentDriverName/currentTrailerLabel resolve the same way as TruckListingRow's.
export type TruckDetailResponse = {
  id: string;
  truckNumber: string;
  statusCode: number | null;
  vin: string | null;
  make: string | null;
  model: string | null;
  year: number | null;
  currentDriverName: string | null;
  currentTrailerLabel: string | null;
  syncedAt: string | null;
};
