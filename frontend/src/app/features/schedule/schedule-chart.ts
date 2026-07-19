// Shared positioning math for the schedule's day header and each driver row's busy segment - percentages (not the
// pixel/viewBox math hos-duty-status-timeline.ts uses) so every row is a plain CSS bar that scales with the
// container width, since here there's one row per driver rather than a single fixed-width chart. The visible window
// is a variable-length [rangeStartMs, rangeStartMs + rangeDays*DAY_MS) span (not always a fixed week) so it can be
// resized via the schedule's date-range picker.
export const DAY_MS = 24 * 60 * 60 * 1000;
export const DEFAULT_RANGE_DAYS = 7;
export const MAX_RANGE_DAYS = 31;

const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export type DayTick = { dayIndex: number; percent: number; label: string; isToday: boolean };

export function startOfDayMs(nowMs: number): number {
  return new Date(nowMs).setHours(0, 0, 0, 0);
}

// One tick per day boundary across the visible range (rangeStartMs through rangeDays-1 days out) - whichever day is
// actually today (per nowMs) is labeled "Today", the rest get a short weekday + date so a dispatcher can tell which
// calendar day a column represents at a glance. rangeStartMs is an arbitrary anchor (not always "today") so the same
// range can be scrolled backward/forward - no day is labeled "Today" when the visible range doesn't contain it.
export function buildDayTicks(rangeStartMs: number, rangeDays: number, nowMs: number = Date.now()): DayTick[] {
  const todayStart = startOfDayMs(nowMs);
  const ticks: DayTick[] = [];
  for (let day = 0; day < rangeDays; day++) {
    const dayStart = rangeStartMs + day * DAY_MS;
    const date = new Date(dayStart);
    const isToday = dayStart === todayStart;
    const label = isToday ? 'Today' : `${WEEKDAY_LABELS[date.getDay()]} ${date.getMonth() + 1}/${date.getDate()}`;
    ticks.push({ dayIndex: day, percent: (day / rangeDays) * 100, label, isToday });
  }
  return ticks;
}

// Clamps epochMs into [rangeStartMs, rangeStartMs + rangeMs] before converting to a 0-100 percent position, so a
// load whose pickup/dropoff falls outside the visible range still renders a segment clipped to its bounds rather
// than overflowing the row or disappearing. Defaults to the default one-week range.
export function percentForTime(
  epochMs: number,
  rangeStartMs: number,
  rangeMs: number = DEFAULT_RANGE_DAYS * DAY_MS
): number {
  const clamped = Math.min(Math.max(epochMs, rangeStartMs), rangeStartMs + rangeMs);
  return ((clamped - rangeStartMs) / rangeMs) * 100;
}

// Whether "now" falls inside the visible range at all - distinct from "rangeStart is exactly today's start", since
// a custom (non-arrow-paged) range can contain today without starting on it (e.g. picking July 10-25 when today is
// July 17). Drives both a driver row's "now" marker and whether the schedule polls for live updates.
export function rangeContainsNow(rangeStartMs: number, rangeDays: number, nowMs: number = Date.now()): boolean {
  return nowMs >= rangeStartMs && nowMs < rangeStartMs + rangeDays * DAY_MS;
}

function pad2(n: number): string {
  return n < 10 ? `0${n}` : `${n}`;
}

function formatMdy(date: Date): string {
  return `${pad2(date.getMonth() + 1)}/${pad2(date.getDate())}/${date.getFullYear()}`;
}

// Renders a [start, end] range as "MM/DD/YYYY - MM/DD/YYYY" for the date-range-picker's read-only trigger display.
// Undefined (nothing picked yet) renders as an empty string rather than throwing.
export function formatDateRange(range: [Date | undefined, Date | undefined] | undefined): string {
  const [start, end] = range ?? [undefined, undefined];
  if (!start || !end) {
    return '';
  }
  return `${formatMdy(start)} - ${formatMdy(end)}`;
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
