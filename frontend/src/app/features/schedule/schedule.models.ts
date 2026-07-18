// One manifest's schedule-relevant fields (mirrors the backend's DriverTimelineRow.ManifestSegment).
// pickupAppointmentStart/eta are the load's scheduled pickup/dropoff appointment times (not actual arrival/departure
// times), used to position and size a segment on the driver's row.
export type ManifestSegment = {
  manifestStatus: string;
  pickupAppointmentStart: string;
  eta: string;
  origin: string | null;
  destination: string | null;
  loadReference: string | null;
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
