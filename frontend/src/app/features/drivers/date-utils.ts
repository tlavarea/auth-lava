// Start of the viewer's local calendar day, as an ISO-8601 instant - used to scope the activity feed to "today" in
// the viewer's own timezone (matching Samsara's own viewer-relative "Today" framing), not the server's.
export function startOfTodayIso(): string {
  const startOfDay = new Date();
  startOfDay.setHours(0, 0, 0, 0);
  return startOfDay.toISOString();
}
