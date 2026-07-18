import { Component, computed, input, InputSignal, Signal } from '@angular/core';

import { HlmBadgeImports } from '@spartan-ng/helm/badge';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '@features/drivers/driver-status';
import { buildHourTicks, HourTick, percentForTime, startOfDayMs } from './timeline-chart';
import { DriverTimelineRow } from './timeline.models';

type BusySegment = { leftPercent: number; widthPercent: number; title: string };

// One row per driver: idle track spanning today, with a single highlighted segment when a vektor_manifest is
// currently matched to this driver (see backend's DriverTimelineService) - from pickupAppointmentStart to eta
// (the load's scheduled pickup/dropoff appointment times), clamped to today's bounds. No per-driver detail route for
// this MVP view; dutyStatus is shown as a supplementary badge, not the busy signal (see plan's "busy signal" choice).
@Component({
  selector: 'app-timeline-driver-row',
  host: { class: 'block' },
  imports: [HlmBadgeImports],
  template: `
    <div class="grid grid-cols-[250px_1fr] items-center gap-2 py-1">
      <div class="flex min-w-0 items-center justify-between">
        <span class="truncate text-sm font-medium">{{ driver().driverName }}</span>
        @if (driver().dutyStatus !== null) {
          <span hlmBadge class="shrink-0" [variant]="driverDutyStatusVariant(driver().dutyStatus)">
            {{ driverDutyStatusLabel(driver().dutyStatus) }}
          </span>
        }
      </div>

      <div class="relative h-8 overflow-hidden rounded bg-muted" [attr.aria-label]="ariaLabel()">
        @for (tick of hourTicks; track tick.hour) {
          <span class="absolute inset-y-0 w-px bg-border" [style.left.%]="tick.percent"></span>
        }
        @if (busySegment(); as segment) {
          <span
            class="absolute inset-y-0 rounded bg-success/70"
            [style.left.%]="segment.leftPercent"
            [style.width.%]="segment.widthPercent"
            [title]="segment.title"></span>
        }
        <span class="absolute inset-y-0 w-0.5 bg-foreground/60" [style.left.%]="nowPercent()"></span>
      </div>
    </div>
  `,
})
export class TimelineDriverRow {
  readonly driver: InputSignal<DriverTimelineRow> = input.required<DriverTimelineRow>();

  protected readonly hourTicks: HourTick[] = buildHourTicks();
  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;

  // Recomputed whenever driver() changes (the ~60s poll in timeline.page.ts) - `now` is captured fresh at that point
  // rather than ticking on its own timer, mirroring hos-duty-status-timeline.ts's chart computed.
  private readonly dayStartMs: Signal<number> = computed(() => startOfDayMs(Date.now()));
  protected readonly nowPercent: Signal<number> = computed(() => percentForTime(Date.now(), this.dayStartMs()));

  protected readonly busySegment: Signal<BusySegment | null> = computed(() => {
    const driver = this.driver();
    if (driver.pickupAppointmentStart === null || driver.eta === null) {
      return null;
    }
    const dayStart = this.dayStartMs();
    const startMs = new Date(driver.pickupAppointmentStart).getTime();
    const endMs = new Date(driver.eta).getTime();
    if (endMs <= startMs) {
      return null;
    }
    const leftPercent = percentForTime(startMs, dayStart);
    const widthPercent = Math.max(percentForTime(endMs, dayStart) - leftPercent, 1);
    const title = [driver.loadReference, driver.destination, `ETA ${driver.eta}`].filter(Boolean).join(' — ');
    return { leftPercent, widthPercent, title };
  });

  protected readonly ariaLabel: Signal<string> = computed(() => {
    const driver = this.driver();
    return this.busySegment() !== null
      ? `${driver.driverName}: on load to ${driver.destination ?? 'unknown destination'}`
      : `${driver.driverName}: idle`;
  });
}
