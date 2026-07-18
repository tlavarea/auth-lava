import { DatePipe } from '@angular/common';
import { Component, computed, input, InputSignal, Signal } from '@angular/core';

import { HlmBadgeImports } from '@spartan-ng/helm/badge';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '@features/drivers/driver-status';
import {
  buildWeekDayTicks,
  DayTick,
  formatCityState,
  percentForTime,
  startOfDayMs,
  WEEK_DAYS,
  WEEK_MS,
} from './schedule-chart';
import { DriverScheduleRow } from './schedule.models';

type BusySegment = { leftPercent: number; widthPercent: number };

// Below this width, a busy segment's own bar isn't wide enough to fit both the origin and destination label blocks
// without them overlapping - drop the (less critical) origin block and show only the destination/load-reference
// block, mirroring how a real short/adjacent segment is handled in the reference schedule UI this was modeled on.
const NARROW_SEGMENT_THRESHOLD_PERCENT = 12;

// One row per driver: idle track spanning the rolling week (today through 6 days out), with a single highlighted
// segment when a vektor_manifest is currently matched to this driver (see backend's DriverTimelineService) - from
// pickupAppointmentStart to eta (the load's scheduled pickup/dropoff appointment times), clamped to the week's
// bounds. No per-driver detail route for this MVP view; dutyStatus is shown as a supplementary badge, not the busy
// signal (see plan's "busy signal" choice). Origin/pickup/destination/load-reference are rendered directly on the
// segment itself rather than behind a hover affordance, so a dispatcher can read a load's details at a glance.
@Component({
  selector: 'app-schedule-driver-row',
  host: { class: 'block' },
  imports: [HlmBadgeImports, DatePipe],
  template: `
    <div class="grid grid-cols-[250px_1fr] items-center gap-2 pb-4">
      <div class="flex min-w-0 items-center justify-between">
        <span class="truncate text-sm font-medium">{{ driver().driverName }}</span>
        @if (driver().dutyStatus !== null) {
          <span hlmBadge class="shrink-0" [variant]="driverDutyStatusVariant(driver().dutyStatus)">
            {{ driverDutyStatusLabel(driver().dutyStatus) }}
          </span>
        }
      </div>

      <div class="relative h-12 overflow-hidden rounded bg-muted" [attr.aria-label]="ariaLabel()">
        @for (tick of dayTicks; track tick.dayIndex) {
          <span
            class="absolute inset-y-0 w-px"
            [class]="{ 'bg-neutral-300': !busySegment(), 'bg-success': busySegment() }"
            [style.left.%]="tick.percent"></span>
        }
        @for (tick of daySegmentTicks; track tick) {
          <span class="absolute inset-y-0 w-px bg-border" [style.left.%]="tick"></span>
        }
        @if (busySegment(); as segment) {
          <div
            class="absolute inset-y-0 flex items-center justify-between gap-1 overflow-hidden rounded bg-success/20 px-1.5 dark:bg-success/25"
            [style.left.%]="segment.leftPercent"
            [style.width.%]="segment.widthPercent">
            @if (driver().origin !== null && segment.widthPercent >= narrowSegmentThresholdPercent) {
              <div class="flex min-w-0 flex-col items-start truncate">
                <span class="truncate text-[11px] font-medium text-foreground">
                  {{ formatCityState(driver().origin) }}
                </span>
                <span class="truncate text-[10px] text-muted-foreground">
                  {{ driver().pickupAppointmentStart | date: 'MMM d, h:mm a' }}
                </span>
              </div>
            }
            <div class="ml-auto flex min-w-0 flex-col items-end truncate">
              <span class="truncate text-[11px] font-medium text-foreground">
                {{ formatCityState(driver().destination) }}
              </span>
              <span class="truncate text-[10px] text-muted-foreground">{{ driver().loadReference }}</span>
            </div>
          </div>
        }
        <span class="absolute inset-y-0 w-0.5 bg-foreground/60" [style.left.%]="nowPercent()"></span>
      </div>
    </div>
  `,
})
export class ScheduleDriverRow {
  readonly driver: InputSignal<DriverScheduleRow> = input.required<DriverScheduleRow>();

  protected readonly dayTicks: DayTick[] = buildWeekDayTicks(Date.now());
  protected readonly daySegmentTicks: number[] = ScheduleDriverRow.buildDaySegmentTicks(this.dayTicks);
  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;
  protected readonly formatCityState = formatCityState;
  protected readonly narrowSegmentThresholdPercent = NARROW_SEGMENT_THRESHOLD_PERCENT;

  // Recomputed whenever driver() changes (the ~60s poll in schedule.page.ts) - `now` is captured fresh at that point
  // rather than ticking on its own timer, mirroring hos-duty-status-timeline.ts's chart computed.
  private readonly weekStartMs: Signal<number> = computed(() => startOfDayMs(Date.now()));
  protected readonly nowPercent: Signal<number> = computed(() =>
    percentForTime(Date.now(), this.weekStartMs(), WEEK_MS)
  );

  protected readonly busySegment: Signal<BusySegment | null> = computed(() => {
    const driver = this.driver();
    if (driver.pickupAppointmentStart === null || driver.eta === null) {
      return null;
    }
    const weekStart = this.weekStartMs();
    const startMs = new Date(driver.pickupAppointmentStart).getTime();
    const endMs = new Date(driver.eta).getTime();
    if (endMs <= startMs) {
      return null;
    }
    const leftPercent = percentForTime(startMs, weekStart, WEEK_MS);
    const widthPercent = Math.max(percentForTime(endMs, weekStart, WEEK_MS) - leftPercent, 1);
    return { leftPercent, widthPercent };
  });

  // Carries the same load detail the removed hover card used to show, since screen-reader users no longer get it
  // from hover-triggered content.
  protected readonly ariaLabel: Signal<string> = computed(() => {
    const driver = this.driver();
    if (this.busySegment() === null) {
      return `${driver.driverName}: idle`;
    }
    const from = driver.origin ?? 'unknown origin';
    const to = driver.destination ?? 'unknown destination';
    const pickup = driver.pickupAppointmentStart
      ? `, pickup ${new Date(driver.pickupAppointmentStart).toLocaleString()}`
      : '';
    const eta = driver.eta ? `, ETA ${new Date(driver.eta).toLocaleString()}` : '';
    const loadReference = driver.loadReference ? ` (load ${driver.loadReference})` : '';
    return `${driver.driverName}: on load from ${from} to ${to}${pickup}${eta}${loadReference}`;
  });

  // One extra faint tick at each day's 1/3 and 2/3 marks, echoing the week header's Morning/Noon/Evening sub-columns
  // so a bar's start/end can be read against the same day-part boundaries the header shows.
  private static buildDaySegmentTicks(dayTicks: DayTick[]): number[] {
    const dayWidthPercent = 100 / WEEK_DAYS;
    return dayTicks.flatMap((tick) => [tick.percent + dayWidthPercent / 3, tick.percent + (2 * dayWidthPercent) / 3]);
  }
}
