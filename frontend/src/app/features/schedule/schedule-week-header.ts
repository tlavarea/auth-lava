import { Component } from '@angular/core';

import { buildWeekDayTicks, DayTick, WEEK_DAYS } from './schedule-chart';

// Rendered once above the driver rows so every row's day-boundary gridlines/segments line up against a shared
// week scale, rather than each row repeating its own labels (see schedule-driver-row.ts, which renders the same
// tick positions as plain gridlines with no text).
@Component({
  selector: 'app-schedule-week-header',
  host: { class: 'block' },
  template: `
    <div class="grid grid-cols-[250px_1fr] items-center gap-2 pb-1">
      <span></span>
      <div class="relative h-4 text-xs text-muted-foreground">
        @for (tick of dayTicks; track tick.dayIndex) {
          <span
            class="absolute -translate-x-1/2"
            [class.font-medium]="tick.isToday"
            [class.text-foreground]="tick.isToday"
            [style.left.%]="tick.percent + dayWidthPercent / 2">
            {{ tick.label }}
          </span>
        }
      </div>
    </div>
  `,
})
export class ScheduleWeekHeader {
  protected readonly dayTicks: DayTick[] = buildWeekDayTicks(Date.now());
  protected readonly dayWidthPercent: number = 100 / WEEK_DAYS;
}
