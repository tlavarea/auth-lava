import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideTruck, lucideUser, lucideX } from '@ng-icons/lucide';
import { HlmAccordionImports } from '@spartan-ng/helm/accordion';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { TrailerDetailResponse } from '../trailers.models';
import { TrailersRequestStatus, TrailersStore, TrailersStoreType } from '../trailers.store';

@Component({
  selector: 'app-trailer-detail',
  imports: [HlmAccordionImports, HlmButtonImports, HlmSpinnerImports, NgIcon, RouterLink],
  viewProviders: [provideIcons({ lucideTruck, lucideUser, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 rounded-md bg-card p-6">
      <div class="flex shrink-0 justify-end">
        <a hlmBtn variant="ghost" size="icon" routerLink=".." aria-label="Back to trailers">
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
          <p class="text-destructive">Couldn't load this trailer.</p>
        }
        @default {
          @if (detail(); as detail) {
            <div class="flex flex-col gap-4 overflow-y-auto">
              <div class="flex flex-col gap-8">
                <div>
                  <h1 class="pb-2 text-2xl font-semibold">{{ detail.label }}</h1>
                  <p class="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                    Trailer • {{ detail.year }} {{ detail.manufacturer }}
                  </p>
                </div>

                <div class="flex flex-col gap-1.5 text-sm">
                  <div class="flex items-center gap-2">
                    <ng-icon name="lucideUser" class="shrink-0 text-muted-foreground" />
                    <span>{{ detail.currentDriverName ?? 'No driver assigned' }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <ng-icon name="lucideTruck" class="shrink-0 text-muted-foreground" />
                    <span>{{ detail.currentTruckNumber ?? 'No truck assigned' }}</span>
                  </div>
                </div>
              </div>

              <hlm-accordion>
                <div hlmAccordionItem [isOpened]="true">
                  <hlm-accordion-trigger>Details</hlm-accordion-trigger>
                  <hlm-accordion-content>
                    <dl class="grid grid-cols-[150px_minmax(200px,1fr)] gap-y-2 text-sm">
                      <dt class="text-muted-foreground">Label</dt>
                      <dd>{{ detail.label }}</dd>
                      <dt class="text-muted-foreground">Manufacturer</dt>
                      <dd>{{ detail.manufacturer ?? '—' }}</dd>
                      <dt class="text-muted-foreground">Year</dt>
                      <dd>{{ detail.year ?? '—' }}</dd>
                      <dt class="text-muted-foreground">VIN</dt>
                      <dd>{{ detail.vin ?? '—' }}</dd>
                      <dt class="text-muted-foreground">License Plate</dt>
                      <dd>{{ detail.licensePlate ?? '—' }}</dd>
                      <dt class="text-muted-foreground">Asset Serial Number</dt>
                      <dd>{{ detail.assetSerialNumber ?? '—' }}</dd>
                    </dl>
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
export class TrailerDetailPage {
  private readonly store: TrailersStoreType = inject(TrailersStore);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Bound from the `:id` route param by withComponentInputBinding() in app.config.ts.
  readonly id: InputSignal<string> = input.required<string>();

  protected readonly detail: Signal<TrailerDetailResponse | null> = this.store.selectedDetail;
  protected readonly status: Signal<TrailersRequestStatus> = this.store.detailStatus;

  constructor() {
    effect(() => void this.store.loadTrailerDetail(this.id()));
    this.destroyRef.onDestroy(() => this.store.clearSelectedDetail());
  }
}
