import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoveLeft, lucideX } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { TruckDetailResponse } from '../trucks.models';
import { TrucksRequestStatus, TrucksStore, TrucksStoreType } from '../trucks.store';

const REFRESH_INTERVAL_MS = 60_000;

@Component({
  selector: 'app-truck-detail',
  imports: [DecimalPipe, HlmButtonImports, HlmSpinnerImports, NgIcon, RouterLink],
  viewProviders: [provideIcons({ lucideMoveLeft, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 rounded-md bg-card p-6">
      <header class="flex h-16 shrink-0 items-center justify-between border-b">
        <a hlmBtn variant="ghost" size="sm" routerLink=".." class="lg:hidden">
          <ng-icon name="lucideMoveLeft" />
          Back to trucks
        </a>

        <h1 class="hidden font-medium lg:flex">{{ detail()?.truckNumber ?? 'Truck ' + id() }}</h1>
        <a hlmBtn variant="ghost" size="icon" routerLink=".." class="hidden lg:inline-flex" aria-label="Back to trucks">
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
          <p class="text-destructive">Couldn't load this truck.</p>
        }
        @default {
          @if (detail(); as detail) {
            <div class="flex flex-col gap-4">
              <dl class="grid grid-cols-[150px_minmax(200px,1fr)] gap-y-2 text-sm">
                <dt class="text-muted-foreground">Truck #</dt>
                <dd>{{ detail.truckNumber }}</dd>
                <dt class="text-muted-foreground">VIN</dt>
                <dd>{{ detail.vin ?? '—' }}</dd>
                <dt class="text-muted-foreground">Make / Model / Year</dt>
                <dd>{{ detail.make }} {{ detail.model }} ({{ detail.year }})</dd>
                <dt class="text-muted-foreground">Current Driver</dt>
                <dd>{{ detail.currentDriverName ?? '—' }}</dd>
                <dt class="text-muted-foreground">Current Trailer</dt>
                <dd>{{ detail.currentTrailerLabel ?? '—' }}</dd>
              </dl>

              @if (hasDiagnostics(detail)) {
                <div class="border-t pt-4">
                  <h2 class="mb-2 text-sm font-medium">Diagnostics</h2>
                  <dl class="grid grid-cols-[150px_minmax(200px,1fr)] gap-y-2 text-sm">
                    <dt class="text-muted-foreground">Fuel</dt>
                    <dd>{{ detail.fuelPercent !== null ? detail.fuelPercent + '%' : '—' }}</dd>
                    <dt class="text-muted-foreground">Odometer</dt>
                    <dd>
                      {{ detail.odometerMiles !== null ? (detail.odometerMiles | number: '1.0-0') + ' mi' : '—' }}
                    </dd>
                    <dt class="text-muted-foreground">Engine Hours</dt>
                    <dd>{{ detail.engineHours !== null ? (detail.engineHours | number: '1.0-0') : '—' }}</dd>
                    <dt class="text-muted-foreground">Engine</dt>
                    <dd>{{ detail.engineState ?? '—' }}</dd>
                    <dt class="text-muted-foreground">Engine RPM</dt>
                    <dd>{{ detail.engineRpm ?? '—' }}</dd>
                    <dt class="text-muted-foreground">Engine Load</dt>
                    <dd>{{ detail.engineLoadPercent !== null ? detail.engineLoadPercent + '%' : '—' }}</dd>
                    <dt class="text-muted-foreground">DEF Level</dt>
                    <dd>
                      {{ detail.defLevelPercent !== null ? (detail.defLevelPercent | number: '1.0-1') + '%' : '—' }}
                    </dd>
                    <dt class="text-muted-foreground">Battery</dt>
                    <dd>{{ detail.batteryVolts !== null ? (detail.batteryVolts | number: '1.1-1') + 'V' : '—' }}</dd>
                    <dt class="text-muted-foreground">Coolant Temp</dt>
                    <dd>{{ detail.coolantTempF !== null ? (detail.coolantTempF | number: '1.0-0') + '°F' : '—' }}</dd>
                    <dt class="text-muted-foreground">Fault Codes</dt>
                    <dd>{{ detail.faultCodes ?? 'None reported' }}</dd>
                    <dt class="text-muted-foreground">Last Location</dt>
                    <dd>{{ detail.formattedLocation ?? '—' }}</dd>
                    <dt class="text-muted-foreground">Location As Of</dt>
                    <dd>{{ detail.locationTime ?? '—' }}</dd>
                  </dl>
                </div>
              } @else {
                <p class="border-t pt-4 text-sm text-muted-foreground">
                  No Samsara diagnostics available for this truck.
                </p>
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

  protected readonly detail: Signal<TruckDetailResponse | null> = this.store.selectedDetail;
  protected readonly status: Signal<TrucksRequestStatus> = this.store.detailStatus;

  // Any one populated diagnostic/location field is enough to show the section - matching a truck to a Samsara
  // vehicle is best-effort (see VinMatchingTruckMatchStrategy), so most fields being present but one or two missing
  // (e.g. no fault codes reported this cycle) is normal, not an error.
  protected hasDiagnostics(detail: TruckDetailResponse): boolean {
    return (
      detail.fuelPercent !== null ||
      detail.odometerMiles !== null ||
      detail.engineHours !== null ||
      detail.engineState !== null ||
      detail.formattedLocation !== null
    );
  }

  constructor() {
    effect(() => void this.store.loadTruckDetail(this.id()));

    // Diagnostics re-sync roughly every 2 minutes server-side (SamsaraVehicleDiagnosticsSyncScheduler) - poll on the
    // same cadence while this detail view is open, same pattern as driver-detail.page.ts's slow poll.
    timer(REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.store.refreshTruckDetail(this.id()));

    this.destroyRef.onDestroy(() => this.store.clearSelectedDetail());
  }
}
