// Formats a millisecond duration as Samsara's own "H:MM" HOS clock display (e.g. 5:38, 22:58) - negative/null
// durations (a clock already exhausted, or not yet synced) render as "0:00" rather than a negative or missing value.
export function formatDurationMs(durationMs: number | null): string {
  if (durationMs === null || durationMs <= 0) {
    return '0:00';
  }

  const totalMinutes = Math.floor(durationMs / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${hours}:${minutes.toString().padStart(2, '0')}`;
}
