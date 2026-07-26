import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoveLeft, lucideX } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { TrailerDetailResponse } from '../trailers.models';
import { TrailersRequestStatus, TrailersStore, TrailersStoreType } from '../trailers.store';

@Component({
  selector: 'app-trailer-detail',
  imports: [HlmButtonImports, HlmSpinnerImports, NgIcon, RouterLink],
  viewProviders: [provideIcons({ lucideMoveLeft, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 rounded-md bg-card p-6">
      <header class="flex h-16 shrink-0 items-center justify-between border-b">
        <a hlmBtn variant="ghost" size="sm" routerLink=".." class="lg:hidden">
          <ng-icon name="lucideMoveLeft" />
          Back to trailers
        </a>

        <h1 class="hidden font-medium lg:flex">{{ detail()?.label ?? 'Trailer ' + id() }}</h1>
        <a
          hlmBtn
          variant="ghost"
          size="icon"
          routerLink=".."
          class="hidden lg:inline-flex"
          aria-label="Back to trailers">
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
          <p class="text-destructive">Couldn't load this trailer.</p>
        }
        @default {
          @if (detail(); as detail) {
            <div class="flex flex-col gap-4">
              <dl class="grid grid-cols-[150px_minmax(200px,1fr)] gap-y-2 text-sm">
                <dt class="text-muted-foreground">Label</dt>
                <dd>{{ detail.label }}</dd>
                <dt class="text-muted-foreground">Manufacturer</dt>
                <dd>{{ detail.manufacturer ?? '—' }}</dd>
                <dt class="text-muted-foreground">Year</dt>
                <dd>{{ detail.year ?? '—' }}</dd>
              </dl>
              <p class="text-sm text-muted-foreground">More details coming soon.</p>
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
