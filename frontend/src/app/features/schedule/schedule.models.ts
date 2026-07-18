// Mirrors the backend's GET /api/drivers/timeline response shape (DriverScheduleRow). The manifest fields
// (manifestStatus through loadReference) are null together when no currently-synced vektor_manifest matches this
// driver - i.e. the driver has no known active load, not a failed lookup. pickupAppointmentStart/eta are the load's
// scheduled pickup/dropoff appointment times (not actual arrival/departure times), used to position and size the
// driver's "busy" block on the schedule.
export type DriverScheduleRow = {
  driverId: string;
  driverName: string;
  activationStatus: string;
  dutyStatus: string | null;
  manifestStatus: string | null;
  pickupAppointmentStart: string | null;
  eta: string | null;
  origin: string | null;
  destination: string | null;
  loadReference: string | null;
};
