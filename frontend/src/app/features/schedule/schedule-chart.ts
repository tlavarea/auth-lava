// Shared "rolling week" positioning math for the schedule's week header and each driver row's busy segment -
// percentages (not the pixel/viewBox math hos-duty-status-timeline.ts uses) so every row is a plain CSS bar that
// scales with the container width, since here there's one row per driver rather than a single fixed-width chart.
export const DAY_MS = 24 * 60 * 60 * 1000;
export const WEEK_DAYS = 7;
export const WEEK_MS = WEEK_DAYS * DAY_MS;

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export type DayTick = { dayIndex: number; percent: number; label: string; isToday: boolean };

export function startOfDayMs(nowMs: number): number {
  return new Date(nowMs).setHours(0, 0, 0, 0);
}

// One tick per day boundary across the rolling week (today through 6 days out) - day 0 is labeled "Today", the rest
// get a short weekday + date so a dispatcher can tell which calendar day a column represents at a glance.
export function buildWeekDayTicks(nowMs: number): DayTick[] {
  const weekStart = startOfDayMs(nowMs);
  const ticks: DayTick[] = [];
  for (let day = 0; day < WEEK_DAYS; day++) {
    const date = new Date(weekStart + day * DAY_MS);
    const label = day === 0 ? 'Today' : `${WEEKDAY_LABELS[date.getDay()]} ${date.getMonth() + 1}/${date.getDate()}`;
    ticks.push({ dayIndex: day, percent: (day / WEEK_DAYS) * 100, label, isToday: day === 0 });
  }
  return ticks;
}

// Clamps epochMs into [rangeStartMs, rangeStartMs + rangeMs] before converting to a 0-100 percent position, so a
// load whose pickup/dropoff falls outside the visible range still renders a segment clipped to its bounds rather
// than overflowing the row or disappearing. Defaults to the full rolling week.
export function percentForTime(epochMs: number, rangeStartMs: number, rangeMs: number = WEEK_MS): number {
  const clamped = Math.min(Math.max(epochMs, rangeStartMs), rangeStartMs + rangeMs);
  return ((clamped - rangeStartMs) / rangeMs) * 100;
}

// Shortens a full street address (e.g. from vektor_manifest's origin/destination, "4251 Turin Dr, Bessemer, AL
// 35020") down to "City, ST" for compact display on a schedule bar. Takes the last two comma-separated segments
// (city, then "ST zip") rather than assuming a fixed segment count, so it degrades gracefully for a two-segment
// "City, ST zip" address too. Falls back to the trimmed input unchanged if it doesn't look like a comma-separated
// address at all, rather than throwing on unexpected formats.
export function formatCityState(address: string | null): string | null {
  if (address === null) {
    return null;
  }
  const parts = address
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
  if (parts.length < 2) {
    return address.trim();
  }
  const city = parts[parts.length - 2];
  const state = parts[parts.length - 1].split(/\s+/)[0];
  return `${city}, ${state}`;
}
