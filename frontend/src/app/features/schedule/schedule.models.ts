// One manifest's schedule-relevant fields (mirrors the backend's DriverTimelineRow.ManifestSegment).
// pickupAppointmentStart/eta are the load's scheduled pickup/dropoff appointment times (not actual arrival/departure
// times), used to position and size a segment on the driver's row. manifestNumber is the stable ID used to look up
// this manifest's route (see ScheduleApi.route).
export type ManifestSegment = {
  manifestNumber: number;
  manifestStatus: string;
  pickupAppointmentStart: string;
  eta: string;
  origin: string | null;
  destination: string | null;
  loadReference: string | null;
};

// Mirrors the backend's ManifestStopResponse - one pickup/dropoff on a manifest's route, in sequenceNumber order.
// arrivedAt/checkedInAt/checkedOutAt are null until the driver actually reaches/checks in/checks out of this stop -
// that's also how a stop's Completed/Arrived/En Route status is derived client-side (see schedule-manifest-detail.ts),
// since Vektor has no separate status field per stop. estimatedMilesToNext/actualMilesToNext/odometerMiles describe
// this stop's outbound leg to the following stop and are null/zero on the last stop, which has no next leg.
export type ManifestStop = {
  stopId: string | null;
  sequenceNumber: number;
  stopType: 'PICKUP' | 'DROPOFF';
  siteName: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  timezoneAbbreviation: string | null;
  appointmentWindowStart: string | null;
  appointmentWindowEnd: string | null;
  arrivedAt: string | null;
  checkedInAt: string | null;
  checkedOutAt: string | null;
  referenceNumbers: string | null;
  notes: string | null;
  contactPhone: string | null;
  estimatedMilesToNext: number | null;
  actualMilesToNext: number | null;
  odometerMiles: number | null;
};

// Mirrors the backend's ManifestStartingPositionResponse - the truck's position when the manifest begins, carried
// over from wherever its previous manifest left off. Not a pickup/dropoff on this manifest, so it's not part of
// ManifestRoute.stops - and not every manifest has one. estimatedMilesToNext/actualMilesToNext/odometerMiles describe
// the leg from here to the first real stop, same meaning as the same-named fields on ManifestStop.
export type ManifestStartingPosition = {
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  note: string | null;
  estimatedMilesToNext: number | null;
  actualMilesToNext: number | null;
  odometerMiles: number | null;
};

// Mirrors the backend's GET /api/manifests/{manifestNumber}/route response shape (ManifestRouteResponse):
// every stop on the manifest plus the driving route (geometry, not just distance/duration) that visits them in
// order. encodedPolyline is Google's polyline-encoded route geometry, decoded client-side via decode-polyline.ts.
export type ManifestRoute = {
  stops: ManifestStop[];
  startingPosition: ManifestStartingPosition | null;
  encodedPolyline: string;
  distanceMeters: number | null;
  duration: string | null;
};

// Mirrors the backend's GET /api/manifests/{manifestNumber}/driver-location response shape
// (ManifestDriverLocationResponse) - the manifest's driver's live location, sourced from Vektor directly (keyed by
// its own driver_id) rather than Samsara's name-matched equivalent. No speed field, unlike the Drivers feature's
// DriverLiveLocationResponse - Vektor's location data doesn't report one.
export type ManifestDriverLocation = {
  latitude: number | null;
  longitude: number | null;
  headingDegrees: number | null;
  asOf: string | null;
  formattedLocation: string | null;
};

// Mirrors the backend's GET /api/manifests/{manifestNumber}/eta response shape (ManifestEtaResponse) - a manifest's
// live ETA to its current active stop (the first stop not yet checked out of). estimatedArrival is Vektor's own
// precomputed value, not something computed client-side.
export type ManifestEta = {
  stopSequenceNumber: number;
  remainingMiles: number | null;
  remainingMinutes: number;
  estimatedArrival: string | null;
};

// Mirrors the backend's GET /api/drivers/timeline response shape (DriverScheduleRow). manifests is empty when no
// vektor_manifest matching this driver overlaps the requested week - i.e. the driver has no load that week, not a
// failed lookup. dutyStatus is always the driver's current status regardless of which week is being viewed, since
// duty status has no history of its own.
export type DriverScheduleRow = {
  driverId: string;
  driverName: string;
  activationStatus: string;
  dutyStatus: string | null;
  manifests: ManifestSegment[];
};
