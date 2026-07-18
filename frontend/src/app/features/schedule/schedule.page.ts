import { Component, computed, DestroyRef, inject, OnInit, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChevronLeft, lucideChevronRight } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { startOfDayMs } from './schedule-chart';
import { ScheduleDriverRow } from './schedule-driver-row';
import { ScheduleWeekHeader } from './schedule-week-header';
import { DriverScheduleRow } from './schedule.models';
import { ScheduleRequestStatus, ScheduleStore, ScheduleStoreType } from './schedule.store';

const REFRESH_INTERVAL_MS = 60_000;

function hasActiveLoad(row: DriverScheduleRow): boolean {
  return row.manifests.length > 0;
}

// Active (on-a-load) drivers first, then idle, each alphabetical by name - so a dispatcher scanning for "who's free"
// finds idle drivers grouped together at a glance rather than interleaved with active ones.
function sortForSchedule(rows: DriverScheduleRow[]): DriverScheduleRow[] {
  return [...rows].sort((a, b) => {
    const activeDiff = Number(hasActiveLoad(b)) - Number(hasActiveLoad(a));
    return activeDiff !== 0 ? activeDiff : a.driverName.localeCompare(b.driverName);
  });
}

@Component({
  selector: 'app-schedule',
  imports: [HlmButtonImports, HlmSpinnerImports, NgIcon, ScheduleWeekHeader, ScheduleDriverRow],
  providers: [ScheduleStore],
  viewProviders: [provideIcons({ lucideChevronLeft, lucideChevronRight })],
  template: `
    <div class="flex h-full flex-col gap-4 p-4">
      <header class="flex shrink-0 items-center justify-between gap-4">
        <h1 class="text-lg font-medium">Driver Schedule</h1>
        <div class="flex items-center gap-1">
          <button
            type="button"
            hlmBtn
            variant="ghost"
            size="icon"
            aria-label="Previous week"
            (click)="goToPreviousWeek()">
            <ng-icon name="lucideChevronLeft" />
          </button>
          @if (!isCurrentWeek()) {
            <button type="button" hlmBtn variant="ghost" size="sm" (click)="goToCurrentWeek()">Today</button>
          }
          <button type="button" hlmBtn variant="ghost" size="icon" aria-label="Next week" (click)="goToNextWeek()">
            <ng-icon name="lucideChevronRight" />
          </button>
        </div>
        <p class="text-sm text-muted-foreground">{{ activeCount() }} active, {{ idleCount() }} idle</p>
      </header>

      @switch (status()) {
        @case ('loading') {
          <div class="flex flex-1 items-center justify-center">
            <hlm-spinner />
          </div>
        }
        @case ('error') {
          <p class="text-destructive">Couldn't load the driver schedule.</p>
        }
        @default {
          <div class="min-h-0 flex-1 overflow-y-auto">
            <app-schedule-week-header [weekStart]="weekStartMs()" />
            @for (driver of sortedRows(); track driver.driverId) {
              <app-schedule-driver-row [driver]="driver" [weekStart]="weekStartMs()" />
            }
          </div>
        }
      }
    </div>
  `,
})
export class SchedulePage implements OnInit {
  private readonly store: ScheduleStoreType = inject(ScheduleStore);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  protected readonly status: Signal<ScheduleRequestStatus> = this.store.status;
  protected readonly weekStartMs: Signal<number> = this.store.weekStartMs;
  protected readonly isCurrentWeek: Signal<boolean> = computed(() => this.weekStartMs() === startOfDayMs(Date.now()));
  protected readonly sortedRows: Signal<DriverScheduleRow[]> = computed(() => sortForSchedule(this.store.rows()));
  protected readonly activeCount: Signal<number> = computed(() => this.sortedRows().filter(hasActiveLoad).length);
  protected readonly idleCount: Signal<number> = computed(() => this.sortedRows().length - this.activeCount());

  ngOnInit(): void {
    void this.store.loadSchedule();

    // The backing data (samsara_driver_duty_status, vektor_manifest) re-syncs on independent 1-20 min cadences -
    // poll on the same ~60s cadence DriversStore uses so the page stays roughly live without a manual refresh. Only
    // while viewing the current week: a past/future week's data is effectively frozen, so refreshing it on a timer
    // would just be wasted requests.
    timer(REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.isCurrentWeek()) {
          void this.store.refreshSchedule();
        }
      });
  }

  protected goToPreviousWeek(): void {
    void this.store.goToPreviousWeek();
  }

  protected goToNextWeek(): void {
    void this.store.goToNextWeek();
  }

  protected goToCurrentWeek(): void {
    void this.store.goToCurrentWeek();
  }
}
