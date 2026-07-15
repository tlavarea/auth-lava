import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { Card } from '@shared/card/card';
import { ShipmentDetailResponse } from '../shipments.models';
import { ShipmentsRequestStatus, ShipmentsStore, ShipmentsStoreType } from '../shipments.store';

@Component({
  selector: 'app-shipment-detail',
  imports: [Card, HlmButtonImports, HlmSpinnerImports, RouterLink],
  template: `
    <div class="flex h-full flex-col gap-4 p-4">
      <a hlmBtn variant="ghost" size="sm" routerLink=".." class="w-fit">&larr; Back to shipments</a>

      @switch (status()) {
        @case ('loading') {
          <div class="flex flex-1 items-center justify-center">
            <hlm-spinner />
          </div>
        }
        @case ('error') {
          <p class="text-destructive">Couldn't load this shipment.</p>
        }
        @default {
          @if (detail(); as detail) {
            <app-card [title]="detail.listing.shipmentId" [description]="detail.listing.status">
              <dl class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                <dt class="text-muted-foreground">Origin</dt>
                <dd>{{ detail.listing.origin }}</dd>
                <dt class="text-muted-foreground">Destination</dt>
                <dd>{{ detail.listing.destination }}</dd>
                <dt class="text-muted-foreground">Pickup</dt>
                <dd>{{ detail.listing.pickupDate }}</dd>
                <dt class="text-muted-foreground">Required delivery</dt>
                <dd>{{ detail.listing.requiredDeliveryDate }}</dd>
                <dt class="text-muted-foreground">SCAC</dt>
                <dd>{{ detail.scac ?? '—' }}</dd>
                <dt class="text-muted-foreground">Total amount</dt>
                <dd>{{ detail.totalAmount ?? '—' }}</dd>
              </dl>
            </app-card>
          }
        }
      }
    </div>
  `,
})
export class ShipmentDetailPage {
  private readonly store: ShipmentsStoreType = inject(ShipmentsStore);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  // Bound from the `:id` route param by withComponentInputBinding() in app.config.ts.
  readonly id: InputSignal<string> = input.required<string>();

  protected readonly detail: Signal<ShipmentDetailResponse | null> = this.store.selectedDetail;
  protected readonly status: Signal<ShipmentsRequestStatus> = this.store.detailStatus;

  constructor() {
    effect(() => {
      const offerId = Number(this.id());
      if (!Number.isNaN(offerId)) {
        void this.store.loadShipmentDetail(offerId);
      }
    });

    this.destroyRef.onDestroy(() => this.store.clearSelectedDetail());
  }
}
