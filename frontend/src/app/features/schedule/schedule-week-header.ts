import { Component } from '@angular/core';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoon, lucideSun, lucideSunrise } from '@ng-icons/lucide';

import { buildWeekDayTicks, DayTick } from './schedule-chart';

type DaySegment = { label: string; icon: string };

const DAY_SEGMENTS: DaySegment[] = [
  { label: 'Morning', icon: 'lucideSunrise' },
  { label: 'Noon', icon: 'lucideSun' },
  { label: 'Evening', icon: 'lucideMoon' },
];

// Rendered once above the driver rows so every row's day-boundary gridlines/segments line up against a shared
// week scale, rather than each row repeating its own labels (see schedule-driver-row.ts, which renders the same
// tick positions as plain gridlines with no text). Each day column is split into a date row and a Morning/Noon/
// Evening sub-row, modeled on Vektor's own schedule header - grid-cols-7 lands on the same 1/7 boundaries the
// percent-based tick/segment math in schedule-chart.ts already produces, so no positioning math changed here.
@Component({
  selector: 'app-schedule-week-header',
  host: { class: 'block' },
  imports: [NgIcon],
  viewProviders: [provideIcons({ lucideSunrise, lucideSun, lucideMoon })],
  template: `
    <div class="grid grid-cols-[250px_1fr] gap-2 pb-1">
      <span></span>
      <div class="grid grid-cols-7">
        @for (tick of dayTicks; track tick.dayIndex; let odd = $odd) {
          <div
            class="border-t-2 border-neutral-400 pb-1 text-center"
            [class]="{
              'bg-muted/50': odd && !tick.isToday,
              'border-t-neutral-950 dark:border-t-neutral-400': tick.isToday,
              'bg-neutral-100 dark:bg-neutral-900': tick.isToday,
              'border-t-transparent': !tick.isToday,
            }">
            <div
              class="pt-1 text-xs font-medium tracking-wide uppercase"
              [class.font-semibold]="tick.isToday"
              [class.text-foreground]="tick.isToday">
              {{ tick.label }}
            </div>
            <div class="grid grid-cols-3">
              @for (segment of daySegments; track segment.label) {
                <div class="flex flex-col items-center gap-0.5 pt-1 text-[10px] text-muted-foreground">
                  <ng-icon size="12" [name]="segment.icon" />
                  {{ segment.label }}
                </div>
              }
            </div>
          </div>
        }
      </div>
    </div>
  `,
})
export class ScheduleWeekHeader {
  protected readonly dayTicks: DayTick[] = buildWeekDayTicks(Date.now());
  protected readonly daySegments: DaySegment[] = DAY_SEGMENTS;
}
