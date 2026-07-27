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

// The truck detail page's route map data for a single day (defaults to "today" - see TrucksApi.routeHistory).
// points/stops are always present (never null), but may be empty when the truck isn't matched to a Samsara vehicle
// or has no GPS history for the window - see the backend's TruckRouteHistoryService javadoc.
export type TruckRouteHistoryResponse = {
  points: TruckRoutePoint[];
  stops: TruckRouteStop[];
};

// One raw GPS sample making up the route map's polyline, time-ordered.
export type TruckRoutePoint = {
  time: string;
  latitude: number;
  longitude: number;
  headingDegrees: number | null;
  speedMph: number | null;
};

// One place the truck stopped for at least 5 minutes - see the backend's TruckRouteHistoryService javadoc for how
// contiguous stopped GPS samples are clustered into these. latitude/longitude/formattedLocation are the cluster's
// centroid/most representative address, not necessarily any single sample's exact values.
export type TruckRouteStop = {
  latitude: number;
  longitude: number;
  formattedLocation: string | null;
  arrivalTime: string;
  departureTime: string;
  stoppedMinutes: number;
};

// One Samsara-flagged safety event for the truck's matched vehicle. address/mediaUrl are pre-formatted/pre-selected
// by the backend's TruckSafetyEventsService - mediaUrl is null when the event has no media attached.
export type TruckSafetyEventEntry = {
  id: string;
  occurredAt: string;
  behaviorLabels: string[];
  latitude: number;
  longitude: number;
  address: string | null;
  driverName: string | null;
  mediaUrl: string | null;
};
