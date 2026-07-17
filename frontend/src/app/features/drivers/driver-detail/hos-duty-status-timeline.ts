import { Component, computed, input, InputSignal, Signal } from '@angular/core';

import { driverDutyStatusLabel, DutyRow, dutyStatusRow } from '../driver-status';
import { DriverActivityEntry } from '../drivers.models';
import { formatDurationHms } from '../format-duration';

const ROW_ORDER: readonly DutyRow[] = ['OFF', 'SB', 'D', 'ON'];
const DAY_MS = 24 * 60 * 60 * 1000;

const LEFT_GUTTER = 28;
const RIGHT_GUTTER = 58;
const HEADER_HEIGHT = 14;
const ROW_HEIGHT = 22;
const PLOT_WIDTH = 696;
const VIEW_WIDTH = LEFT_GUTTER + PLOT_WIDTH + RIGHT_GUTTER;
const VIEW_HEIGHT = HEADER_HEIGHT + ROW_ORDER.length * ROW_HEIGHT;

type HourTick = { hour: number; x: number; label: string };
type RowBand = { row: DutyRow; top: number; centerY: number; totalMs: number };
type HoverSegment = { key: string; x: number; y: number; width: number; title: string };
type ChartData = {
  hourTicks: HourTick[];
  rows: RowBand[];
  linePath: string;
  hoverSegments: HoverSegment[];
  totalMs: number;
};

function topForRow(row: DutyRow): number {
  return HEADER_HEIGHT + ROW_ORDER.indexOf(row) * ROW_HEIGHT;
}

function centerYForRow(row: DutyRow): number {
  return topForRow(row) + ROW_HEIGHT / 2;
}

function xForTime(epochMs: number, dayStartMs: number): number {
  const clamped = Math.min(Math.max(epochMs, dayStartMs), dayStartMs + DAY_MS);
  return LEFT_GUTTER + ((clamped - dayStartMs) / DAY_MS) * PLOT_WIDTH;
}

function buildChart(entries: DriverActivityEntry[], nowMs: number): ChartData {
  const dayStartMs = new Date(nowMs).setHours(0, 0, 0, 0);

  // A gridline every hour, but only every 3rd hour gets a text label - at the width this chart renders beside the
  // HOS clock rings on desktop, labeling all 25 ticks crowds them past legible (see hos-duty-status-timeline.spec.ts
  // and the layout mockup used to verify this).
  const hourTicks: HourTick[] = [];
  for (let hour = 0; hour <= 24; hour++) {
    const label =
      hour % 3 !== 0 ? '' : hour === 0 || hour === 24 ? 'M' : hour === 12 ? 'N' : String(hour > 12 ? hour - 12 : hour);
    hourTicks.push({ hour, x: LEFT_GUTTER + (hour / 24) * PLOT_WIDTH, label });
  }

  // Oldest-first, clipped to [start of today, now] - Samsara's /fleet/hos/logs (see DriverActivityEntry) returns
  // newest-first with a null endTime on the still-open current status.
  const segments = entries
    .map((entry) => ({
      entry,
      row: dutyStatusRow(entry.dutyStatus),
      startMs: new Date(entry.startTime).getTime(),
      endMs: Math.min(entry.endTime === null ? nowMs : new Date(entry.endTime).getTime(), nowMs),
    }))
    .filter((segment) => segment.endMs > segment.startMs)
    .sort((a, b) => a.startMs - b.startMs);

  const totalMsByRow = new Map<DutyRow, number>(ROW_ORDER.map((row) => [row, 0]));
  const hoverSegments: HoverSegment[] = [];
  let linePath = '';
  let previousEndMs: number | null = null;

  for (const segment of segments) {
    totalMsByRow.set(segment.row, (totalMsByRow.get(segment.row) ?? 0) + (segment.endMs - segment.startMs));

    const x1 = xForTime(segment.startMs, dayStartMs);
    const x2 = xForTime(segment.endMs, dayStartMs);
    const y = centerYForRow(segment.row);
    // A gap against the previous segment (missing log data) breaks the line rather than fabricating a connector.
    linePath += previousEndMs === segment.startMs ? ` L ${x1} ${y} L ${x2} ${y}` : ` M ${x1} ${y} L ${x2} ${y}`;
    previousEndMs = segment.endMs;

    const label = driverDutyStatusLabel(segment.entry.dutyStatus);
    const duration = formatDurationHms(segment.endMs - segment.startMs);
    const remark = segment.entry.remark ? ` — ${segment.entry.remark}` : '';
    hoverSegments.push({
      key: `${segment.entry.startTime}-${segment.entry.dutyStatus ?? ''}`,
      x: x1,
      y: topForRow(segment.row),
      width: Math.max(x2 - x1, 1),
      title: `${label}: ${duration}${remark}`,
    });
  }

  const rows: RowBand[] = ROW_ORDER.map((row) => ({
    row,
    top: topForRow(row),
    centerY: centerYForRow(row),
    totalMs: totalMsByRow.get(row) ?? 0,
  }));

  return {
    hourTicks,
    rows,
    linePath: linePath.trim(),
    hoverSegments,
    totalMs: rows.reduce((sum, row) => sum + row.totalMs, 0),
  };
}

// Today's duty-status history (see DriversStore.loadDriverActivity, scoped to the viewer's local calendar day) as a
// classic ELD "grid graph": a step line across the 4 FMCSA duty-status rows, gridded by hour. Hand-built SVG rather
// than a charting library, matching hos-clock-ring.ts (no chart primitive exists in libs/ui). Each segment also
// carries a native <title> tooltip, but that's a hover bonus, not the accessible path: the aria-label on the <svg>
// gives the per-row totals as text, and the adjacent app-driver-activity-feed list already gives every segment its
// own non-hover, textual home - so this component doesn't duplicate that with a custom tooltip/legend layer.
@Component({
  selector: 'app-hos-duty-status-timeline',
  host: { class: 'block' },
  template: `
    <svg class="h-auto w-full" role="img" [attr.viewBox]="viewBox" [attr.aria-label]="ariaLabel()">
      @for (tick of chart().hourTicks; track tick.hour) {
        <line
          class="stroke-border"
          stroke-width="1"
          [attr.x1]="tick.x"
          [attr.x2]="tick.x"
          [attr.y1]="HEADER_HEIGHT"
          [attr.y2]="VIEW_HEIGHT" />
        @if (tick.label) {
          <text
            text-anchor="middle"
            class="fill-muted-foreground"
            font-size="8"
            [attr.x]="tick.x"
            [attr.y]="HEADER_HEIGHT - 3">
            {{ tick.label }}
          </text>
        }
      }

      @for (row of chart().rows; track row.row) {
        <line
          class="stroke-border"
          stroke-width="1"
          [attr.x1]="LEFT_GUTTER"
          [attr.x2]="LEFT_GUTTER + PLOT_WIDTH"
          [attr.y1]="row.top"
          [attr.y2]="row.top" />
        <text
          text-anchor="end"
          dominant-baseline="middle"
          class="fill-muted-foreground"
          font-size="9"
          [attr.x]="LEFT_GUTTER - 5"
          [attr.y]="row.centerY">
          {{ row.row }}
        </text>
        <text
          dominant-baseline="middle"
          class="fill-muted-foreground tabular-nums"
          font-size="9"
          [attr.x]="LEFT_GUTTER + PLOT_WIDTH + 6"
          [attr.y]="row.centerY">
          {{ formatDurationHms(row.totalMs) }}
        </text>
      }
      <line
        class="stroke-border"
        stroke-width="1"
        [attr.x1]="LEFT_GUTTER"
        [attr.x2]="LEFT_GUTTER + PLOT_WIDTH"
        [attr.y1]="VIEW_HEIGHT"
        [attr.y2]="VIEW_HEIGHT" />

      <path
        fill="none"
        class="stroke-blue-600 dark:stroke-blue-400"
        stroke-width="2"
        stroke-linejoin="round"
        stroke-linecap="round"
        [attr.d]="chart().linePath" />

      @for (segment of chart().hoverSegments; track segment.key) {
        <rect
          fill="transparent"
          [attr.x]="segment.x"
          [attr.y]="segment.y"
          [attr.width]="segment.width"
          [attr.height]="ROW_HEIGHT">
          <title>{{ segment.title }}</title>
        </rect>
      }
    </svg>
    <p class="mt-1 text-end text-xs text-muted-foreground">
      Total <span class="tabular-nums">{{ formatDurationHms(chart().totalMs) }}</span>
    </p>
  `,
})
export class HosDutyStatusTimeline {
  readonly entries: InputSignal<DriverActivityEntry[]> = input.required<DriverActivityEntry[]>();

  protected readonly viewBox = `0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`;
  protected readonly VIEW_HEIGHT = VIEW_HEIGHT;
  protected readonly LEFT_GUTTER = LEFT_GUTTER;
  protected readonly PLOT_WIDTH = PLOT_WIDTH;
  protected readonly HEADER_HEIGHT = HEADER_HEIGHT;
  protected readonly ROW_HEIGHT = ROW_HEIGHT;

  protected readonly formatDurationHms = formatDurationHms;

  // Rebuilt whenever entries() changes (the ~60s poll in driver-detail.page.ts) - `now` is captured fresh at that
  // point rather than ticking on its own timer, mirroring driver-detail.page.ts's elapsedSinceDutyStatus.
  protected readonly chart: Signal<ChartData> = computed(() => buildChart(this.entries(), Date.now()));

  protected readonly ariaLabel: Signal<string> = computed(() => {
    const summary = this.chart()
      .rows.map((row) => `${row.row} ${formatDurationHms(row.totalMs)}`)
      .join(', ');
    return `Today's hours of service by status: ${summary}`;
  });
}
