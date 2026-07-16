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
  // HOS clock fields (all in milliseconds) and dutyStatusSince are null together with dutyStatus - no HOS clock data
  // synced yet, or the driver's Samsara Driver app is disconnected. See backend's SamsaraDriverDutyStatusSyncTasklet.
  driveRemainingDurationMs: number | null;
  shiftRemainingDurationMs: number | null;
  cycleRemainingDurationMs: number | null;
  timeUntilBreakDurationMs: number | null;
  dutyStatusSince: string | null;
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

// A live, on-demand single-vehicle GPS fetch (see backend's SamsaraDriverLiveLocationService) - fresher than
// DriverDetailResponse's location fields, which only refresh on the ~1 min batch cadence. Fields are null together
// when the live Samsara call returns no GPS payload for the vehicle.
export type DriverLiveLocationResponse = {
  latitude: number | null;
  longitude: number | null;
  heading: number | null;
  speed: number | null;
  locationTime: string | null;
  formattedLocation: string | null;
};

// One duty-status change from Samsara's /fleet/hos/logs, fetched live on every request by the backend (see
// SamsaraDriverActivityService) - not persisted, so there's no syncedAt. endTime is null for the driver's current
// (still-open) status; latitude/longitude are null when Samsara recorded no location for that log entry.
export type DriverActivityEntry = {
  dutyStatus: string | null;
  startTime: string;
  endTime: string | null;
  latitude: number | null;
  longitude: number | null;
  remark: string | null;
};
