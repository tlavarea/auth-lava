export type DriverListingRow = {
  id: string;
  name: string;
  activationStatus: string;
  currentVehicleName: string | null;
  dutyStatus: string | null;
  currentLocation: string | null;
};

// Location fields (latitude through formattedLocation) are null together when the driver has no current vehicle
// assignment synced, or independently null (assignment present, location fields null) when the assigned vehicle
// has no synced location yet - both are normal states, not errors. See the backend's DriverDetailResponse javadoc.
export type DriverDetailResponse = {
  id: string;
  name: string;
  username: string | null;
  email: string | null;
  phone: string | null;
  licenseNumber: string | null;
  licenseState: string | null;
  activationStatus: string;
  dutyStatus: string | null;
  tags: string | null;
  currentVehicleId: string | null;
  currentVehicleName: string | null;
  latitude: number | null;
  longitude: number | null;
  heading: number | null;
  speed: number | null;
  locationTime: string | null;
  formattedLocation: string | null;
  rawResponse: string | null;
  syncedAt: string | null;
};
