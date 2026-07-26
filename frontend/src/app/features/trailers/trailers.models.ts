// currentTruckNumber is a reverse lookup over vektor_truck.current_trailer_id - null when no truck currently
// claims this trailer. See the backend's TrailerServiceImpl javadoc.
export type TrailerListingRow = {
  id: string;
  label: string;
  manufacturer: string | null;
  year: number | null;
  currentTruckNumber: string | null;
};

// Placeholder detail response.
export type TrailerDetailResponse = {
  id: string;
  label: string;
  manufacturer: string | null;
  year: number | null;
  syncedAt: string | null;
};
