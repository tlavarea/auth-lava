import { Component } from '@angular/core';

import { buildHourTicks, HourTick } from './timeline-chart';

// Rendered once above the driver rows so every row's gridlines/segment line up against a shared hour scale, rather
// than each row repeating its own labels (see driver-timeline-row.ts, which renders the same tick positions as plain
// gridlines with no text).
@Component({
  selector: 'app-timeline-hour-header',
  host: { class: 'block' },
  template: `
    <div class="grid grid-cols-[160px_1fr] items-center gap-2">
      <span></span>
      <div class="relative h-4 text-xs text-muted-foreground">
        @for (tick of hourTicks; track tick.hour) {
          @if (tick.label) {
            <span class="absolute -translate-x-1/2" [style.left.%]="tick.percent">{{ tick.label }}</span>
          }
        }
      </div>
    </div>
  `,
})
export class TimelineHourHeader {
  protected readonly hourTicks: HourTick[] = buildHourTicks();
}
