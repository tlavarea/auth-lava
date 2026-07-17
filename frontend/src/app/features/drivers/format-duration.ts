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

// Formats a millisecond duration as H:MM:SS (e.g. 9:19:11) - the ELD grid-graph's per-row total format, distinct
// from formatDurationMs's H:MM (Samsara's own HOS clock display, which doesn't carry seconds).
export function formatDurationHms(durationMs: number | null): string {
  if (durationMs === null || durationMs <= 0) {
    return '0:00:00';
  }

  const totalSeconds = Math.floor(durationMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}
