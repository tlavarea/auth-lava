// Shared "today" positioning math for the timeline's hour header and each driver row's busy segment - percentages
// (not the pixel/viewBox math hos-duty-status-timeline.ts uses) so every row is a plain CSS bar that scales with the
// container width, since here there's one row per driver rather than a single fixed-width chart.
const DAY_MS = 24 * 60 * 60 * 1000;

export type HourTick = { hour: number; percent: number; label: string };

export function startOfDayMs(nowMs: number): number {
  return new Date(nowMs).setHours(0, 0, 0, 0);
}

// A gridline every hour, but only every 3rd hour gets a text label - same crowding reasoning as
// hos-duty-status-timeline.ts's buildChart.
export function buildHourTicks(): HourTick[] {
  const ticks: HourTick[] = [];
  for (let hour = 0; hour <= 24; hour++) {
    const label =
      hour % 3 !== 0 ? '' : hour === 0 || hour === 24 ? 'M' : hour === 12 ? 'N' : String(hour > 12 ? hour - 12 : hour);
    ticks.push({ hour, percent: (hour / 24) * 100, label });
  }
  return ticks;
}

// Clamps epochMs into [dayStartMs, dayStartMs + 24h] before converting to a 0-100 percent position, so a load whose
// pickup/dropoff falls outside today still renders a segment clipped to today's bounds rather than overflowing the
// row or disappearing.
export function percentForTime(epochMs: number, dayStartMs: number): number {
  const clamped = Math.min(Math.max(epochMs, dayStartMs), dayStartMs + DAY_MS);
  return ((clamped - dayStartMs) / DAY_MS) * 100;
}
