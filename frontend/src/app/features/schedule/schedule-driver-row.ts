import { DatePipe } from '@angular/common';
import { Component, computed, input, InputSignal, Signal } from '@angular/core';

import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmHoverCardImports } from '@spartan-ng/helm/hover-card';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '@features/drivers/driver-status';
import { buildWeekDayTicks, DayTick, percentForTime, startOfDayMs, WEEK_MS } from './schedule-chart';
import { DriverScheduleRow } from './schedule.models';

type BusySegment = { leftPercent: number; widthPercent: number };

// One row per driver: idle track spanning the rolling week (today through 6 days out), with a single highlighted
// segment when a vektor_manifest is currently matched to this driver (see backend's DriverTimelineService) - from
// pickupAppointmentStart to eta (the load's scheduled pickup/dropoff appointment times), clamped to the week's
// bounds. No per-driver detail route for this MVP view; dutyStatus is shown as a supplementary badge, not the busy
// signal (see plan's "busy signal" choice).
@Component({
  selector: 'app-schedule-driver-row',
  host: { class: 'block' },
  imports: [HlmBadgeImports, HlmHoverCardImports, DatePipe],
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
        @if (busySegment(); as segment) {
          <hlm-hover-card class="contents">
            <span
              hlmHoverCardTrigger
              class="absolute inset-y-0 rounded bg-success/50"
              [style.left.%]="segment.leftPercent"
              [style.width.%]="segment.widthPercent"></span>
            <hlm-hover-card-content *hlmHoverCardPortal class="space-y-2">
              <div class="flex items-center justify-between gap-2">
                <span class="font-medium">{{ driver().loadReference }}</span>
                @if (driver().dutyStatus !== null) {
                  <span hlmBadge [variant]="driverDutyStatusVariant(driver().dutyStatus)">
                    {{ driverDutyStatusLabel(driver().dutyStatus) }}
                  </span>
                }
              </div>
              <p class="text-muted-foreground">{{ driver().destination }}</p>
              <dl class="grid grid-cols-[auto_1fr] gap-x-2 gap-y-1 text-xs text-muted-foreground">
                <dt>Pickup</dt>
                <dd>{{ driver().pickupAppointmentStart | date: 'EEE, MMM d h:mm a' }}</dd>
                <dt>ETA</dt>
                <dd>{{ driver().eta | date: 'EEE, MMM d h:mm a' }}</dd>
              </dl>
            </hlm-hover-card-content>
          </hlm-hover-card>
        }
        <span class="absolute inset-y-0 w-0.5 bg-foreground/60" [style.left.%]="nowPercent()"></span>
      </div>
    </div>
  `,
})
export class ScheduleDriverRow {
  readonly driver: InputSignal<DriverScheduleRow> = input.required<DriverScheduleRow>();

  protected readonly dayTicks: DayTick[] = buildWeekDayTicks(Date.now());
  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;

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

  protected readonly ariaLabel: Signal<string> = computed(() => {
    const driver = this.driver();
    return this.busySegment() !== null
      ? `${driver.driverName}: on load to ${driver.destination ?? 'unknown destination'}`
      : `${driver.driverName}: idle`;
  });
}
