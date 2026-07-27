// engineState/ecuSpeedMph are joined from Samsara diagnostics by the truck's matched Samsara vehicle id - both null
// when the truck isn't VIN-matched yet or has no synced diagnostics. See truck-status.ts for how the displayed
// Off/On/Idle/Moving status is derived from them - rather than Vektor's own statusCode (a raw, unconfirmed integer,
// not surfaced here). currentDriverName/currentTrailerLabel are resolved from vektor_truck's own
// current_driver_id/current_trailer_id, null when unassigned or when the referenced driver/trailer isn't currently
// synced - see the backend's TruckServiceImpl javadoc.
export type TruckListingRow = {
  id: string;
  truckNumber: string;
  engineState: string | null;
  ecuSpeedMph: number | null;
  currentDriverName: string | null;
  currentTrailerLabel: string | null;
};

// currentDriverName/currentTrailerLabel resolve the same way as TruckListingRow's. The diagnostic/location fields
// below are only populated when the truck was VIN-matched to a Samsara vehicle (see the backend's
// VinMatchingTruckMatchStrategy) - null otherwise, same "unassigned or stale id" convention. Values are already
// converted to display units by the backend (odometer in miles, engine hours in hours, DEF level/battery/coolant
// temp in percent/volts/Fahrenheit) - see TruckServiceImpl. faultCodes is Samsara's raw fault-code JSON as a string,
// passed through as-is rather than parsed client-side.
export type TruckDetailResponse = {
  id: string;
  truckNumber: string;
  statusCode: number | null;
  vin: string | null;
  licensePlate: string | null;
  make: string | null;
  model: string | null;
  year: number | null;
  currentDriverName: string | null;
  currentTrailerLabel: string | null;
  syncedAt: string | null;
  fuelPercent: number | null;
  odometerMiles: number | null;
  engineHours: number | null;
  faultCodes: string | null;
  engineState: string | null;
  ecuSpeedMph: number | null;
  defLevelPercent: number | null;
  batteryVolts: number | null;
  coolantTempF: number | null;
  engineRpm: number | null;
  engineLoadPercent: number | null;
  latitude: number | null;
  longitude: number | null;
  formattedLocation: string | null;
  locationTime: string | null;
};
