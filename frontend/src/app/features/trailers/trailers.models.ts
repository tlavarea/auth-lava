// currentTruckNumber is a reverse lookup over vektor_truck.current_trailer_id - null when no truck currently
// claims this trailer. See the backend's TrailerServiceImpl javadoc.
export type TrailerListingRow = {
  id: string;
  label: string;
  manufacturer: string | null;
  year: number | null;
  currentTruckNumber: string | null;
};

// vin comes from Vektor's own trailer data; licensePlate/assetSerialNumber come from the Samsara trailer matched by
// VIN (see the backend's VinMatchingTrailerMatchStrategy) - null when unmatched. currentTruckNumber/currentDriverName
// are reverse lookups over the truck currently pulling this trailer, same convention as TrailerListingRow.
export type TrailerDetailResponse = {
  id: string;
  label: string;
  manufacturer: string | null;
  year: number | null;
  vin: string | null;
  licensePlate: string | null;
  assetSerialNumber: string | null;
  currentTruckNumber: string | null;
  currentDriverName: string | null;
  syncedAt: string | null;
};
