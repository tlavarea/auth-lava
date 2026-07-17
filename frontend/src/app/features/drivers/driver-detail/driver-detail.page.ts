import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoveLeft, lucideX } from '@ng-icons/lucide';
import { HlmAccordionImports } from '@spartan-ng/helm/accordion';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '../driver-status';
import { DriverActivityEntry, DriverDetailResponse } from '../drivers.models';
import { DriversRequestStatus, DriversStore, DriversStoreType } from '../drivers.store';
import { formatDurationMs } from '../format-duration';
import { DriverActivityFeed } from './driver-activity-feed';
import { DriverLocationMap } from './driver-location-map';
import { HosClockRing } from './hos-clock-ring';
import { HosDutyStatusTimeline } from './hos-duty-status-timeline';

const REFRESH_INTERVAL_MS = 60_000;
const LIVE_LOCATION_POLL_INTERVAL_MS = 15_000;

// Presentational denominators for the HOS clock rings below - the FMCSA property-carrying-driver defaults (11hr
// drive, 14hr shift, 8hr time-until-break, 70hr/8-day cycle). Samsara's response only carries each clock's *remaining*
// duration, not the driver's actual applicable ruleset (which varies by carrier config, e.g. 60hr/7-day cycles) - these
// are only used to size each ring's fill percentage, not asserted as the driver's real limit.
const DRIVE_CLOCK_TOTAL_MS = 11 * 3_600_000;
const SHIFT_CLOCK_TOTAL_MS = 14 * 3_600_000;
const BREAK_CLOCK_TOTAL_MS = 8 * 3_600_000;
const CYCLE_CLOCK_TOTAL_MS = 70 * 3_600_000;

@Component({
  selector: 'app-driver-detail',
  imports: [
    HlmAccordionImports,
    HlmBadgeImports,
    HlmButtonImports,
    HlmSpinnerImports,
    NgIcon,
    RouterLink,
    DriverLocationMap,
    HosClockRing,
    HosDutyStatusTimeline,
    DriverActivityFeed,
  ],
  viewProviders: [provideIcons({ lucideMoveLeft, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 rounded-md bg-card p-6">
      <header class="flex h-16 shrink-0 items-center justify-between border-b">
        <a hlmBtn variant="ghost" size="sm" routerLink=".." class="lg:hidden">
          <ng-icon name="lucideMoveLeft" />
          Back to drivers
        </a>

        <h1 class="hidden font-medium lg:flex">{{ detail()?.name ?? 'Driver ' + id() }}</h1>
        <a
          hlmBtn
          variant="ghost"
          size="icon"
          routerLink=".."
          class="hidden lg:inline-flex"
          aria-label="Back to drivers">
          <ng-icon name="lucideX" />
        </a>
      </header>

      @switch (status()) {
        @case ('loading') {
          <div class="flex flex-1 items-center justify-center">
            <hlm-spinner />
          </div>
        }
        @case ('error') {
          <p class="text-destructive">Couldn't load this driver.</p>
        }
        @default {
          @if (detail(); as detail) {
            <div class="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto">
              <dl class="grid shrink-0 grid-cols-[150px_minmax(200px,1fr)_150px_minmax(200px,1fr)] gap-y-2 text-sm">
                <dt class="text-muted-foreground">Status</dt>
                <dd>{{ detail.activationStatus }}</dd>
                <dt class="text-muted-foreground">Username</dt>
                <dd>{{ detail.username ?? '—' }}</dd>

                <dt class="text-muted-foreground">Email</dt>
                <dd>{{ detail.email ?? '—' }}</dd>
                <dt class="text-muted-foreground">Phone</dt>
                <dd>{{ detail.phone ?? '—' }}</dd>

                <dt class="text-muted-foreground">License Number</dt>
                <dd>{{ detail.licenseNumber ?? '—' }}</dd>
                <dt class="text-muted-foreground">License State</dt>
                <dd>{{ detail.licenseState ?? '—' }}</dd>

                <dt class="text-muted-foreground">Tags</dt>
                <dd>{{ detail.tags ?? '—' }}</dd>
                <dt class="text-muted-foreground">Current Vehicle</dt>
                <dd>{{ detail.currentVehicleName ?? '—' }}</dd>

                <dt class="text-muted-foreground">Location</dt>
                <dd>{{ detail.formattedLocation ?? '—' }}</dd>
                <dt class="text-muted-foreground">Location As Of</dt>
                <dd>{{ detail.locationTime ?? '—' }}</dd>
              </dl>

              @if (detail.dutyStatus !== null) {
                <hlm-accordion class="shrink-0">
                  <div hlmAccordionItem [isOpened]="true">
                    <hlm-accordion-trigger>Hours of Service</hlm-accordion-trigger>
                    <hlm-accordion-content>
                      <div class="flex flex-col gap-4 lg:flex-row lg:flex-wrap lg:items-center lg:gap-8">
                        <div class="flex flex-wrap items-center gap-8">
                          <div class="flex flex-col items-center gap-1">
                            <span
                              hlmBadge
                              class="flex h-10 w-10 items-center justify-center rounded-full p-0 text-base"
                              aria-hidden="true"
                              [variant]="driverDutyStatusVariant(detail.dutyStatus)">
                              {{ driverDutyStatusLabel(detail.dutyStatus).charAt(0) }}
                            </span>
                            <span class="text-sm font-medium">{{
                              formatDurationMs(elapsedSinceDutyStatus(detail))
                            }}</span>
                            <span class="text-xs text-muted-foreground">{{
                              driverDutyStatusLabel(detail.dutyStatus)
                            }}</span>
                          </div>
                          <app-hos-clock-ring
                            label="Break"
                            [remainingMs]="detail.timeUntilBreakDurationMs"
                            [totalMs]="BREAK_CLOCK_TOTAL_MS" />
                          <app-hos-clock-ring
                            label="Drive"
                            [remainingMs]="detail.driveRemainingDurationMs"
                            [totalMs]="DRIVE_CLOCK_TOTAL_MS" />
                          <app-hos-clock-ring
                            label="Shift"
                            [remainingMs]="detail.shiftRemainingDurationMs"
                            [totalMs]="SHIFT_CLOCK_TOTAL_MS" />
                          <app-hos-clock-ring
                            label="Cycle"
                            [remainingMs]="detail.cycleRemainingDurationMs"
                            [totalMs]="CYCLE_CLOCK_TOTAL_MS" />
                        </div>
                        <app-hos-duty-status-timeline
                          class="min-w-0 lg:min-w-[420px] lg:flex-1"
                          [entries]="activity()" />
                      </div>
                    </hlm-accordion-content>
                  </div>
                </hlm-accordion>
              } @else {
                <p class="shrink-0 text-sm text-muted-foreground">No HOS data available for this driver.</p>
              }

              <div class="relative min-h-0 flex-1">
                @if (detail.latitude !== null && detail.longitude !== null) {
                  <app-driver-location-map
                    class="absolute inset-0"
                    [latitude]="detail.latitude"
                    [longitude]="detail.longitude"
                    [heading]="detail.heading"
                    [speed]="detail.speed"
                    [formattedLocation]="detail.formattedLocation" />
                } @else {
                  <p class="text-sm text-muted-foreground">No current location available for this driver.</p>
                }

                <!-- Desktop: floats over the map's start (left) side, always expanded (no collapse trigger). Not
                     itself scrolling - app-driver-activity-feed's own host owns the scroll region (only its entry
                     list scrolls, "Activity"/"Today"/location stay pinned). -->
                <section
                  class="absolute inset-y-20 inset-s-4 z-10 hidden min-h-0 w-96 flex-col rounded-md border bg-card/95 shadow-lg backdrop-blur lg:flex">
                  <h2 class="shrink-0 p-4 font-medium">Activity</h2>
                  @switch (activityStatus()) {
                    @case ('loading') {
                      <div class="flex flex-1 items-center justify-center p-4">
                        <hlm-spinner />
                      </div>
                    }
                    @case ('error') {
                      <p class="p-4 text-sm text-muted-foreground">Couldn't load activity.</p>
                    }
                    @default {
                      <app-driver-activity-feed
                        [entries]="activity()"
                        [asOf]="detail.locationTime ?? detail.syncedAt"
                        [currentLocation]="detail.formattedLocation" />
                    }
                  }
                </section>
              </div>

              <!-- Mobile: closed-by-default accordion below the map. -->
              <hlm-accordion class="shrink-0 lg:hidden">
                <div hlmAccordionItem [isOpened]="false">
                  <hlm-accordion-trigger>Activity</hlm-accordion-trigger>
                  <hlm-accordion-content>
                    @switch (activityStatus()) {
                      @case ('loading') {
                        <div class="flex items-center justify-center py-4">
                          <hlm-spinner />
                        </div>
                      }
                      @case ('error') {
                        <p class="text-sm text-muted-foreground">Couldn't load activity.</p>
                      }
                      @default {
                        <app-driver-activity-feed
                          [entries]="activity()"
                          [asOf]="detail.locationTime ?? detail.syncedAt"
                          [currentLocation]="detail.formattedLocation" />
                      }
                    }
                  </hlm-accordion-content>
                </div>
              </hlm-accordion>
            </div>
          }
        }
      }
    </div>
  `,
})
export class DriverDetailPage {
  private readonly store: DriversStoreType = inject(DriversStore);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Bound from the `:id` route param by withComponentInputBinding() in app.config.ts.
  readonly id: InputSignal<string> = input.required<string>();

  protected readonly detail: Signal<DriverDetailResponse | null> = this.store.selectedDetail;
  protected readonly status: Signal<DriversRequestStatus> = this.store.detailStatus;
  protected readonly activity: Signal<DriverActivityEntry[]> = this.store.activity;
  protected readonly activityStatus: Signal<DriversRequestStatus> = this.store.activityStatus;

  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;
  protected readonly formatDurationMs = formatDurationMs;

  protected readonly DRIVE_CLOCK_TOTAL_MS = DRIVE_CLOCK_TOTAL_MS;
  protected readonly SHIFT_CLOCK_TOTAL_MS = SHIFT_CLOCK_TOTAL_MS;
  protected readonly BREAK_CLOCK_TOTAL_MS = BREAK_CLOCK_TOTAL_MS;
  protected readonly CYCLE_CLOCK_TOTAL_MS = CYCLE_CLOCK_TOTAL_MS;

  // Not a signal - deliberately recomputed once per change-detection pass (same "as of last sync" freshness as every
  // other field on `detail`) rather than ticking live every second, to avoid a dedicated per-second timer for a
  // display-only elapsed time.
  protected elapsedSinceDutyStatus(detail: DriverDetailResponse): number | null {
    return detail.dutyStatusSince === null ? null : Date.now() - new Date(detail.dutyStatusSince).getTime();
  }

  // Sequenced (detail, then activity), not fired in parallel - two near-simultaneous requests on the very first
  // load of this view (e.g. a cold page load/hard refresh straight into a driver's detail URL) can both hit an
  // about-to-expire session at once, each independently triggering authErrorInterceptor's refresh-and-retry with no
  // de-dupe between them. Awaiting the detail call first means any needed refresh happens on one request, not two
  // racing ones, before the activity call goes out.
  private async loadDetailThenActivity(id: string): Promise<void> {
    await this.store.loadDriverDetail(id);
    await this.store.loadDriverActivity(id);
  }

  constructor() {
    effect(() => void this.loadDetailThenActivity(this.id()));

    // Full detail (HOS clocks, vehicle assignment, etc) re-syncs roughly every minute server-side
    // (SamsaraLocationSyncScheduler/SamsaraDriverDutyStatusSyncScheduler) - poll on the same cadence while this detail
    // view is open so the rest of the page stays roughly live without a manual refresh.
    timer(REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        void this.store.refreshDriverDetail(this.id());
        void this.store.refreshDriverActivity(this.id());
      });

    // The map's position, polled separately and faster: an on-demand, single-vehicle Samsara call (see
    // DriversApi.liveLocation) rather than a wait for the batch cadence above, so the arrow visibly moves while this
    // view is open instead of jumping once a minute.
    timer(LIVE_LOCATION_POLL_INTERVAL_MS, LIVE_LOCATION_POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.store.pollLiveLocation(this.id()));

    this.destroyRef.onDestroy(() => this.store.clearSelectedDetail());
  }
}
