import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideExternalLink, lucideMapPin, lucideTruck, lucideUser, lucideX } from '@ng-icons/lucide';
import { HlmAccordionImports } from '@spartan-ng/helm/accordion';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmProgressImports } from '@spartan-ng/helm/progress';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { truckStatus, truckStatusBadge } from '../truck-status';
import { TruckDetailResponse, TruckRouteHistoryResponse, TruckSafetyEventEntry } from '../trucks.models';
import { TrucksRequestStatus, TrucksStore, TrucksStoreType } from '../trucks.store';
import { TruckRouteMap } from './truck-route-map';

const REFRESH_INTERVAL_MS = 60_000;

@Component({
  selector: 'app-truck-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    HlmAccordionImports,
    HlmBadgeImports,
    HlmButtonImports,
    HlmProgressImports,
    HlmSpinnerImports,
    NgIcon,
    RouterLink,
    TruckRouteMap,
  ],
  viewProviders: [provideIcons({ lucideExternalLink, lucideMapPin, lucideTruck, lucideUser, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 rounded-md bg-card p-6">
      <div class="flex shrink-0 justify-end">
        <a hlmBtn variant="ghost" size="icon" routerLink=".." aria-label="Back to trucks">
          <ng-icon name="lucideX" />
        </a>
      </div>

      @switch (status()) {
        @case ('loading') {
          <div class="flex flex-1 items-center justify-center">
            <hlm-spinner />
          </div>
        }
        @case ('error') {
          <p class="text-destructive">Couldn't load this truck.</p>
        }
        @default {
          @if (detail(); as detail) {
            <div class="flex h-full min-h-0 flex-col gap-4">
              <div class="flex flex-col gap-4 overflow-y-auto">
                <div class="flex flex-col gap-8">
                  <div>
                    <h1 class="pb-2 text-2xl font-semibold">{{ detail.truckNumber }}</h1>
                    <p class="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                      Vehicle • {{ detail.year }} {{ detail.make }} {{ detail.model }}
                    </p>
                    @if (truckStatus(detail.engineState, detail.ecuSpeedMph) === 'moving') {
                      <p class="font-semibold text-success">{{ detail.ecuSpeedMph | number: '1.0-0' }} MPH</p>
                    }
                  </div>

                  <div class="flex flex-col gap-1.5 text-sm">
                    <div class="flex items-center gap-2">
                      <ng-icon name="lucideUser" class="shrink-0 text-muted-foreground" />
                      <span>{{ detail.currentDriverName ?? 'No driver assigned' }}</span>
                    </div>
                    <div class="flex items-center gap-2">
                      <ng-icon name="lucideTruck" class="shrink-0 text-muted-foreground" />
                      <span>{{ detail.currentTrailerLabel ?? 'No trailer assigned' }}</span>
                    </div>
                    @if (detail.formattedLocation !== null) {
                      <div class="flex items-center gap-2">
                        <ng-icon name="lucideMapPin" class="shrink-0 text-muted-foreground" />
                        <span>{{ detail.formattedLocation }}</span>
                        @if (mapsUrl(detail); as url) {
                          <a
                            class="relative top-0.5"
                            target="_blank"
                            rel="noopener noreferrer"
                            aria-label="Open in Google Maps"
                            [href]="url">
                            <ng-icon name="lucideExternalLink" class="shrink-0 text-muted-foreground" />
                          </a>
                        }
                      </div>
                      @if (detail.locationTime !== null) {
                        <div class="flex items-center gap-2">
                          <span class="w-3.5 shrink-0">&nbsp;</span>
                          <span class="text-xs text-muted-foreground">{{
                            detail.locationTime | date: 'MMM d, y h:mm a'
                          }}</span>
                        </div>
                      }
                    }
                  </div>
                </div>

                <hlm-accordion>
                  <div hlmAccordionItem [isOpened]="true">
                    <hlm-accordion-trigger>Details</hlm-accordion-trigger>
                    <hlm-accordion-content>
                      <dl class="grid grid-cols-[150px_minmax(200px,1fr)] gap-y-2 text-sm">
                        <dt class="text-muted-foreground">Truck #</dt>
                        <dd>{{ detail.truckNumber }}</dd>
                        <dt class="text-muted-foreground">VIN</dt>
                        <dd>{{ detail.vin ?? '—' }}</dd>
                        <dt class="text-muted-foreground">License Plate</dt>
                        <dd>{{ detail.licensePlate ?? '—' }}</dd>
                      </dl>
                    </hlm-accordion-content>
                  </div>
                </hlm-accordion>

                @if (hasDiagnostics(detail)) {
                  <hlm-accordion>
                    <div hlmAccordionItem [isOpened]="true">
                      <hlm-accordion-trigger>Diagnostics</hlm-accordion-trigger>
                      <hlm-accordion-content>
                        <dl class="grid grid-cols-[150px_minmax(200px,1fr)] gap-y-2 text-sm">
                          <dt class="text-muted-foreground">Status</dt>
                          <dd>
                            @let badge = truckStatusBadge(detail.engineState, detail.ecuSpeedMph);
                            <span hlmBadge [variant]="badge.variant" [class]="badge.class">{{ badge.label }}</span>
                          </dd>
                          <dt class="text-muted-foreground">Road Speed</dt>
                          <dd>
                            {{ detail.ecuSpeedMph !== null ? (detail.ecuSpeedMph | number: '1.0-0') + ' mph' : '—' }}
                          </dd>
                          <dt class="text-muted-foreground">Fuel</dt>
                          <dd>
                            @if (detail.fuelPercent !== null) {
                              <div class="flex items-center gap-2">
                                <hlm-progress class="h-2 w-32 rounded-none" [value]="detail.fuelPercent">
                                  <hlm-progress-indicator
                                    [class]="{
                                      'bg-green-600': detail.fuelPercent >= 50,
                                      'bg-amber-500': detail.fuelPercent >= 25 && detail.fuelPercent < 50,
                                      'bg-red-600': detail.fuelPercent < 25,
                                    }" />
                                </hlm-progress>
                                <span>{{ detail.fuelPercent }}%</span>
                              </div>
                            } @else {
                              —
                            }
                          </dd>
                          <dt class="text-muted-foreground">DEF Level</dt>
                          <dd>
                            @if (detail.defLevelPercent !== null) {
                              <div class="flex items-center gap-2">
                                <hlm-progress class="h-2 w-32 rounded-none" [value]="detail.defLevelPercent">
                                  <hlm-progress-indicator
                                    [class]="{
                                      'bg-green-600': detail.defLevelPercent >= 50,
                                      'bg-amber-500': detail.defLevelPercent >= 25 && detail.defLevelPercent < 50,
                                      'bg-red-600': detail.defLevelPercent < 25,
                                    }" />
                                </hlm-progress>
                                <span>{{ detail.defLevelPercent | number: '1.0-1' }}%</span>
                              </div>
                            } @else {
                              —
                            }
                          </dd>
                          <dt class="text-muted-foreground">Odometer</dt>
                          <dd>
                            {{ detail.odometerMiles !== null ? (detail.odometerMiles | number: '1.0-0') + ' mi' : '—' }}
                          </dd>
                          <dt class="text-muted-foreground">Engine Hours</dt>
                          <dd>{{ detail.engineHours !== null ? (detail.engineHours | number: '1.0-0') : '—' }}</dd>
                          <dt class="text-muted-foreground">Engine RPM</dt>
                          <dd>{{ detail.engineRpm ?? '—' }}</dd>
                          <dt class="text-muted-foreground">Engine Load</dt>
                          <dd>{{ detail.engineLoadPercent !== null ? detail.engineLoadPercent + '%' : '—' }}</dd>
                          <dt class="text-muted-foreground">Battery</dt>
                          <dd>
                            {{ detail.batteryVolts !== null ? (detail.batteryVolts | number: '1.1-1') + 'V' : '—' }}
                          </dd>
                          <dt class="text-muted-foreground">Coolant Temp</dt>
                          <dd>
                            {{ detail.coolantTempF !== null ? (detail.coolantTempF | number: '1.0-0') + '°F' : '—' }}
                          </dd>
                        </dl>
                      </hlm-accordion-content>
                    </div>
                  </hlm-accordion>
                } @else {
                  <p class="border-t pt-4 text-sm text-muted-foreground">
                    No Samsara diagnostics available for this truck.
                  </p>
                }
              </div>

              @if (routeHistoryStatus() === 'loading' || safetyEventsStatus() === 'loading') {
                <div class="flex flex-1 items-center justify-center border-t pt-4">
                  <hlm-spinner />
                </div>
              } @else if (hasMapData()) {
                <app-truck-route-map
                  class="flex min-h-0 flex-1 flex-col border-t pt-4"
                  [points]="routeHistory()?.points ?? []"
                  [stops]="routeHistory()?.stops ?? []"
                  [safetyEvents]="safetyEvents() ?? []"
                  [driverName]="detail.currentDriverName" />
              } @else {
                <p class="border-t pt-4 text-sm text-muted-foreground">No route history available for today.</p>
              }
            </div>
          }
        }
      }
    </div>
  `,
})
export class TruckDetailPage {
  private readonly store: TrucksStoreType = inject(TrucksStore);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Bound from the `:id` route param by withComponentInputBinding() in app.config.ts.
  readonly id: InputSignal<string> = input.required<string>();

  protected readonly truckStatus = truckStatus;
  protected readonly truckStatusBadge = truckStatusBadge;

  protected readonly detail: Signal<TruckDetailResponse | null> = this.store.selectedDetail;
  protected readonly status: Signal<TrucksRequestStatus> = this.store.detailStatus;

  protected readonly routeHistory: Signal<TruckRouteHistoryResponse | null> = this.store.routeHistory;
  protected readonly routeHistoryStatus: Signal<TrucksRequestStatus> = this.store.routeHistoryStatus;
  protected readonly safetyEvents: Signal<TruckSafetyEventEntry[] | null> = this.store.safetyEvents;
  protected readonly safetyEventsStatus: Signal<TrucksRequestStatus> = this.store.safetyEventsStatus;

  // True once route-history/safety-events data has loaded and at least one of points/stops/safetyEvents is non-empty
  // - a truck that's never been VIN-matched to a Samsara vehicle (see VinMatchingTruckMatchStrategy), or simply
  // hasn't moved/had an event yet today, legitimately has nothing to show here.
  protected readonly hasMapData: Signal<boolean> = computed(() => {
    const routeHistory = this.routeHistory();
    const safetyEvents = this.safetyEvents() ?? [];
    return (routeHistory?.points.length ?? 0) > 0 || (routeHistory?.stops.length ?? 0) > 0 || safetyEvents.length > 0;
  });

  // Any one populated diagnostic/location field is enough to show the section - matching a truck to a Samsara
  // vehicle is best-effort (see VinMatchingTruckMatchStrategy), so most fields being present but one or two missing
  // (e.g. no engine RPM reported this cycle) is normal, not an error.
  protected hasDiagnostics(detail: TruckDetailResponse): boolean {
    return (
      detail.fuelPercent !== null ||
      detail.odometerMiles !== null ||
      detail.engineHours !== null ||
      detail.engineState !== null ||
      detail.formattedLocation !== null
    );
  }

  protected mapsUrl(detail: TruckDetailResponse): string | null {
    return detail.latitude !== null && detail.longitude !== null
      ? `https://www.google.com/maps?q=${detail.latitude},${detail.longitude}`
      : null;
  }

  constructor() {
    effect(() => {
      const id = this.id();
      void this.store.loadTruckDetail(id);
      // Fetched once per truck detail visit, not on the 60s diagnostics-refresh timer below - a full day of GPS
      // history/safety events doesn't meaningfully change every 60s (see TrucksStore.loadTruckMapData).
      void this.store.loadTruckMapData(id);
    });

    // Diagnostics re-sync roughly every 2 minutes server-side (SamsaraVehicleDiagnosticsSyncScheduler) - poll on the
    // same cadence while this detail view is open, same pattern as driver-detail.page.ts's slow poll.
    timer(REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.store.refreshTruckDetail(this.id()));

    this.destroyRef.onDestroy(() => {
      this.store.clearSelectedDetail();
      this.store.clearMapData();
    });
  }
}
