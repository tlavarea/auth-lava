import { DatePipe } from '@angular/common';
import { Component, computed, input, InputSignal, output, OutputEmitterRef, Signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { HlmBadgeImports } from '@spartan-ng/helm/badge';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '@features/drivers/driver-status';
import { buildDayTicks, DAY_MS, DayTick, formatCityState, percentForTime, rangeContainsNow } from './schedule-chart';
import { DriverScheduleRow, ManifestSegment } from './schedule.models';

// Mirrors schedule-week-header.ts's column sizing exactly, so each row's track stretches/overflows in lockstep with
// the shared header above it (day-boundary gridlines and segments would otherwise drift out of alignment).
const MIN_DAY_COLUMN_PX = 138;
const NAME_COLUMN_PX = 250;

type BusySegment = {
  manifestNumber: number;
  leftPercent: number;
  widthPercent: number;
  origin: string | null;
  destination: string | null;
  pickupAppointmentStart: string;
  eta: string;
  loadReference: string | null;
};

// Below this width, a busy segment's own bar isn't wide enough to fit both the origin and destination label blocks
// without them overlapping - drop the (less critical) origin block and show only the destination/load-reference
// block, mirroring how a real short/adjacent segment is handled in the reference schedule UI this was modeled on.
const NARROW_SEGMENT_THRESHOLD_PERCENT = 12;

// One row per driver: idle track spanning the visible week, with one highlighted segment per vektor_manifest whose
// scheduled pickup->dropoff window overlaps that week (see backend's DriverTimelineService#findForWeek) - a driver
// can have several loads in one week now that vektor_manifest retains history instead of only "what's active right
// now". No per-driver detail route for this MVP view; dutyStatus is shown as a supplementary badge, not the busy
// signal (see plan's "busy signal" choice). Origin/pickup/destination/load-reference are rendered directly on each
// segment rather than behind a hover affordance, so a dispatcher can read a load's details at a glance.
@Component({
  selector: 'app-schedule-driver-row',
  host: { class: 'block' },
  imports: [HlmBadgeImports, DatePipe, RouterLink],
  template: `
    <div
      class="grid grid-cols-[250px_1fr] pb-4"
      [style.min-width.px]="NAME_COLUMN_PX + rangeDays() * MIN_DAY_COLUMN_PX">
      <div class="sticky left-0 z-10 flex h-full min-w-0 items-center justify-between self-stretch bg-background pr-2">
        <a class="truncate text-sm font-medium hover:underline" [routerLink]="['/drivers', driver().driverId]">
          {{ driver().driverName }}
        </a>
        @if (driver().dutyStatus !== null) {
          <span hlmBadge class="shrink-0" [variant]="driverDutyStatusVariant(driver().dutyStatus)">
            {{ driverDutyStatusLabel(driver().dutyStatus) }}
          </span>
        }
      </div>

      <div class="relative h-12 overflow-hidden rounded bg-muted pl-2" [attr.aria-label]="ariaLabel()">
        @for (tick of dayTicks(); track tick.dayIndex) {
          <span
            class="absolute inset-y-0 w-px"
            [class]="{ 'bg-neutral-300': busySegments().length === 0, 'bg-success': busySegments().length > 0 }"
            [style.left.%]="tick.percent"></span>
        }
        @for (tick of daySegmentTicks(); track tick) {
          <span class="absolute inset-y-0 w-px bg-border" [style.left.%]="tick"></span>
        }
        @for (segment of busySegments(); track segment.manifestNumber) {
          <div
            class="absolute inset-y-0 flex cursor-pointer items-center justify-between gap-1 overflow-hidden rounded bg-success/20 px-1.5 outline-offset-1 hover:bg-success/30 dark:bg-success/25 dark:hover:bg-success/35"
            role="button"
            tabindex="0"
            [style.left.%]="segment.leftPercent"
            [style.width.%]="segment.widthPercent"
            [attr.aria-label]="segmentAriaLabel(segment)"
            (click)="onSegmentClick(segment)"
            (keydown.enter)="onSegmentClick(segment)"
            (keydown.space)="onSegmentKeydownSpace($event, segment)">
            @if (segment.origin !== null && segment.widthPercent >= narrowSegmentThresholdPercent) {
              <div class="flex min-w-0 flex-col items-start truncate">
                <span class="truncate text-[11px] font-medium text-foreground">
                  {{ formatCityState(segment.origin) }}
                </span>
                <span class="truncate text-[10px] text-muted-foreground">
                  {{ segment.pickupAppointmentStart | date: 'MMM d, h:mm a' }}
                </span>
              </div>
            }
            <div class="ml-auto flex min-w-0 flex-col items-end truncate">
              <span class="truncate text-[11px] font-medium text-foreground">
                {{ formatCityState(segment.destination) }}
              </span>
              <span class="truncate text-[10px] text-muted-foreground">{{ segment.loadReference }}</span>
            </div>
          </div>
        }
        @if (showNowMarker()) {
          <span class="absolute inset-y-0 w-0.5 bg-foreground/60" [style.left.%]="nowPercent()"></span>
        }
      </div>
    </div>
  `,
})
export class ScheduleDriverRow {
  readonly driver: InputSignal<DriverScheduleRow> = input.required<DriverScheduleRow>();
  readonly rangeStart: InputSignal<number> = input.required<number>();
  readonly rangeDays: InputSignal<number> = input.required<number>();

  readonly manifestSelected: OutputEmitterRef<{ driverId: string; manifest: ManifestSegment }> = output();

  protected readonly NAME_COLUMN_PX = NAME_COLUMN_PX;
  protected readonly MIN_DAY_COLUMN_PX = MIN_DAY_COLUMN_PX;

  protected readonly dayTicks: Signal<DayTick[]> = computed(() => buildDayTicks(this.rangeStart(), this.rangeDays()));
  protected readonly daySegmentTicks: Signal<number[]> = computed(() =>
    ScheduleDriverRow.buildDaySegmentTicks(this.dayTicks(), this.rangeDays())
  );
  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;
  protected readonly formatCityState = formatCityState;
  protected readonly narrowSegmentThresholdPercent = NARROW_SEGMENT_THRESHOLD_PERCENT;

  // Only meaningful (and rendered) when the visible range actually contains today - a "now" marker positioned in a
  // past or future range wouldn't correspond to anything on that row. Uses "contains", not "starts on", today so a
  // custom range (e.g. picked from the date-range-picker) still shows the marker even when today isn't day one.
  protected readonly showNowMarker: Signal<boolean> = computed(() =>
    rangeContainsNow(this.rangeStart(), this.rangeDays())
  );
  protected readonly nowPercent: Signal<number> = computed(() =>
    percentForTime(Date.now(), this.rangeStart(), this.rangeDays() * DAY_MS)
  );

  protected readonly busySegments: Signal<BusySegment[]> = computed(() => {
    const rangeStart = this.rangeStart();
    const rangeMs = this.rangeDays() * DAY_MS;
    return this.driver()
      .manifests.map((manifest) => toBusySegment(manifest, rangeStart, rangeMs))
      .filter((segment): segment is BusySegment => segment !== null);
  });

  // Carries the same load detail the removed hover card used to show, since screen-reader users no longer get it
  // from hover-triggered content.
  protected readonly ariaLabel: Signal<string> = computed(() => {
    const driver = this.driver();
    const segments = this.busySegments();
    if (segments.length === 0) {
      return `${driver.driverName}: idle`;
    }
    return `${driver.driverName}: ${segments.length} load${segments.length === 1 ? '' : 's'} this week`;
  });

  protected segmentAriaLabel(segment: BusySegment): string {
    const from = segment.origin ?? 'unknown origin';
    const to = segment.destination ?? 'unknown destination';
    const pickup = `, pickup ${new Date(segment.pickupAppointmentStart).toLocaleString()}`;
    const eta = `, ETA ${new Date(segment.eta).toLocaleString()}`;
    const loadReference = segment.loadReference ? ` (load ${segment.loadReference})` : '';
    return `On load from ${from} to ${to}${pickup}${eta}${loadReference}`;
  }

  protected onSegmentClick(segment: BusySegment): void {
    const manifest = this.driver().manifests.find((m) => m.manifestNumber === segment.manifestNumber);
    if (manifest) {
      this.manifestSelected.emit({ driverId: this.driver().driverId, manifest });
    }
  }

  // Space normally scrolls the page for a non-native interactive element - prevent that before activating, same as
  // a real <button> would (which never triggers a scroll on Space).
  protected onSegmentKeydownSpace(event: Event, segment: BusySegment): void {
    event.preventDefault();
    this.onSegmentClick(segment);
  }

  // One extra faint tick at each day's 1/3 and 2/3 marks, echoing the week header's Morning/Noon/Evening sub-columns
  // so a bar's start/end can be read against the same day-part boundaries the header shows.
  private static buildDaySegmentTicks(dayTicks: DayTick[], rangeDays: number): number[] {
    const dayWidthPercent = 100 / rangeDays;
    return dayTicks.flatMap((tick) => [tick.percent + dayWidthPercent / 3, tick.percent + (2 * dayWidthPercent) / 3]);
  }
}

function toBusySegment(manifest: ManifestSegment, rangeStart: number, rangeMs: number): BusySegment | null {
  const startMs = new Date(manifest.pickupAppointmentStart).getTime();
  const endMs = new Date(manifest.eta).getTime();
  if (endMs <= startMs) {
    return null;
  }
  const leftPercent = percentForTime(startMs, rangeStart, rangeMs);
  const widthPercent = Math.max(percentForTime(endMs, rangeStart, rangeMs) - leftPercent, 1);
  return {
    manifestNumber: manifest.manifestNumber,
    leftPercent,
    widthPercent,
    origin: manifest.origin,
    destination: manifest.destination,
    pickupAppointmentStart: manifest.pickupAppointmentStart,
    eta: manifest.eta,
    loadReference: manifest.loadReference,
  };
}
