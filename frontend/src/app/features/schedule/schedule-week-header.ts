import { Component, computed, input, InputSignal, Signal } from '@angular/core';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoon, lucideSun, lucideSunrise } from '@ng-icons/lucide';

import { buildDayTicks, DayTick } from './schedule-chart';

type DaySegment = { label: string; icon: string };

const DAY_SEGMENTS: DaySegment[] = [
  { label: 'Morning', icon: 'lucideSunrise' },
  { label: 'Noon', icon: 'lucideSun' },
  { label: 'Evening', icon: 'lucideMoon' },
];

// The width below which a day column can't shrink further - past a week's worth of columns, this is what forces
// the row wider than the viewport (triggering schedule.page.ts's horizontal scroll) instead of squeezing every
// column down to unreadable slivers.
const MIN_DAY_COLUMN_PX = 138;
const NAME_COLUMN_PX = 250;

// Rendered once above the driver rows so every row's day-boundary gridlines/segments line up against a shared
// scale, rather than each row repeating its own labels (see schedule-driver-row.ts, which renders the same tick
// positions as plain gridlines with no text). Each day column is split into a date row and a Morning/Noon/Evening
// sub-row, modeled on Vektor's own schedule header. Columns use `minmax(138px, 1fr)` so a range of a week or less
// still stretches evenly to fill the available width (matching the old fixed grid-cols-7 behavior), while a longer
// range clamps every column to the 138px floor and overflows instead of shrinking further - at that point the
// Morning/Noon/Evening text no longer fits, so it's hidden in favor of the icon alone.
@Component({
  selector: 'app-schedule-week-header',
  host: { class: 'block' },
  imports: [NgIcon],
  viewProviders: [provideIcons({ lucideSunrise, lucideSun, lucideMoon })],
  template: `
    <div
      class="grid grid-cols-[250px_1fr] pb-1"
      [style.min-width.px]="NAME_COLUMN_PX + rangeDays() * MIN_DAY_COLUMN_PX">
      <div class="sticky left-0 z-20 h-full w-full self-stretch bg-background"></div>
      <div class="grid pl-2" [style.grid-template-columns]="dayColumnsStyle()">
        @for (tick of dayTicks(); track tick.dayIndex; let odd = $odd) {
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
                  @if (showSegmentLabels()) {
                    {{ segment.label }}
                  }
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
  readonly rangeStart: InputSignal<number> = input.required<number>();
  readonly rangeDays: InputSignal<number> = input.required<number>();

  protected readonly NAME_COLUMN_PX = NAME_COLUMN_PX;
  protected readonly MIN_DAY_COLUMN_PX = MIN_DAY_COLUMN_PX;

  protected readonly dayTicks: Signal<DayTick[]> = computed(() => buildDayTicks(this.rangeStart(), this.rangeDays()));
  protected readonly daySegments: DaySegment[] = DAY_SEGMENTS;
  protected readonly showSegmentLabels: Signal<boolean> = computed(() => this.rangeDays() <= 7);
  protected readonly dayColumnsStyle: Signal<string> = computed(
    () => `repeat(${this.rangeDays()}, minmax(${MIN_DAY_COLUMN_PX}px, 1fr))`
  );
}
