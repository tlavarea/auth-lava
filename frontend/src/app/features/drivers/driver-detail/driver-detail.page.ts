import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoveLeft, lucideX } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { DriverDetailResponse } from '../drivers.models';
import { DriversRequestStatus, DriversStore, DriversStoreType } from '../drivers.store';
import { DriverLocationMap } from './driver-location-map';

const REFRESH_INTERVAL_MS = 60_000;

@Component({
  selector: 'app-driver-detail',
  imports: [HlmButtonImports, HlmSpinnerImports, NgIcon, RouterLink, DriverLocationMap],
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

              @if (detail.latitude !== null && detail.longitude !== null) {
                <app-driver-location-map
                  [latitude]="detail.latitude"
                  [longitude]="detail.longitude"
                  [formattedLocation]="detail.formattedLocation" />
              } @else {
                <p class="shrink-0 text-sm text-muted-foreground">No current location available for this driver.</p>
              }
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

  constructor() {
    effect(() => {
      void this.store.loadDriverDetail(this.id());
    });

    // Vehicle locations re-sync roughly every minute server-side (SamsaraLocationSyncScheduler) - poll on the same
    // cadence while this detail view is open so the map stays roughly live without a manual refresh.
    timer(REFRESH_INTERVAL_MS, REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.store.refreshDriverDetail(this.id()));

    this.destroyRef.onDestroy(() => this.store.clearSelectedDetail());
  }
}
