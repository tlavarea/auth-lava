import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoveLeft, lucideX } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { TruckDetailResponse } from '../trucks.models';
import { TrucksRequestStatus, TrucksStore, TrucksStoreType } from '../trucks.store';

@Component({
  selector: 'app-truck-detail',
  imports: [HlmButtonImports, HlmSpinnerImports, NgIcon, RouterLink],
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
              <p class="text-sm text-muted-foreground">More details coming soon.</p>
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

  constructor() {
    effect(() => void this.store.loadTruckDetail(this.id()));
    this.destroyRef.onDestroy(() => this.store.clearSelectedDetail());
  }
}
