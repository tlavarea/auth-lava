import { Component, computed, DestroyRef, inject, OnInit, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChevronLeft, lucideChevronRight, lucideX } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { formatCityState, startOfDayMs } from './schedule-chart';
import { ScheduleDriverRow } from './schedule-driver-row';
import { ScheduleManifestMap } from './schedule-manifest-map';
import { ScheduleWeekHeader } from './schedule-week-header';
import { DriverScheduleRow, ManifestSegment } from './schedule.models';
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
  imports: [HlmButtonImports, HlmSpinnerImports, NgIcon, ScheduleWeekHeader, ScheduleDriverRow, ScheduleManifestMap],
  providers: [ScheduleStore],
  viewProviders: [provideIcons({ lucideChevronLeft, lucideChevronRight, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 p-4">
      <header class="grid shrink-0 grid-cols-[250px_1fr] items-center gap-4">
        <h1 class="truncate text-lg font-medium">Driver Schedule</h1>
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-1">
            <button
              type="button"
              hlmBtn
              variant="outline"
              size="icon"
              aria-label="Previous week"
              (click)="goToPreviousWeek()">
              <ng-icon name="lucideChevronLeft" />
            </button>
            @if (!isCurrentWeek()) {
              <button type="button" hlmBtn variant="outline" size="sm" (click)="goToCurrentWeek()">Today</button>
            }
            <button type="button" hlmBtn variant="outline" size="icon" aria-label="Next week" (click)="goToNextWeek()">
              <ng-icon name="lucideChevronRight" />
            </button>
          </div>
          <p class="text-sm text-muted-foreground">{{ activeCount() }} active, {{ idleCount() }} idle</p>
        </div>
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
          <div class="flex min-h-0 flex-1 flex-col gap-4">
            <div [class]="selectedManifest() ? 'shrink-0' : 'min-h-0 flex-1 overflow-y-auto'">
              <app-schedule-week-header [weekStart]="weekStartMs()" />
              @for (driver of sortedRows(); track driver.driverId) {
                <app-schedule-driver-row
                  [driver]="driver"
                  [weekStart]="weekStartMs()"
                  (manifestSelected)="onManifestSelected($event)" />
              }
            </div>

            @if (selectedManifest(); as manifest) {
              <div class="flex min-h-0 flex-1 flex-col gap-2 rounded-md border bg-card p-2">
                <div class="flex items-center justify-between">
                  <p class="text-sm font-medium">
                    {{ formatCityState(manifest.origin) }} → {{ formatCityState(manifest.destination) }}
                  </p>
                  <button
                    type="button"
                    hlmBtn
                    variant="ghost"
                    size="icon"
                    aria-label="Close route map"
                    (click)="closeMap()">
                    <ng-icon name="lucideX" />
                  </button>
                </div>
                <app-schedule-manifest-map
                  class="min-h-0 flex-1"
                  [driverId]="selectedDriverId()!"
                  [manifest]="manifest" />
              </div>
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
  protected readonly selectedDriverId: Signal<string | null> = this.store.selectedDriverId;
  protected readonly selectedManifest: Signal<ManifestSegment | null> = this.store.selectedManifest;
  protected readonly formatCityState = formatCityState;

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

  protected onManifestSelected(event: { driverId: string; manifest: ManifestSegment }): void {
    this.store.selectManifest(event.driverId, event.manifest);
  }

  protected closeMap(): void {
    this.store.clearSelection();
  }
}
