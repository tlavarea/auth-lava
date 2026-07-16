import { Component, DestroyRef, effect, inject, input, InputSignal, Signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoveLeft, lucideThumbsDown, lucideThumbsUp, lucideX } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { ShipmentDetailResponse } from '../shipments.models';
import { ShipmentsRequestStatus, ShipmentsStore, ShipmentsStoreType } from '../shipments.store';
import { AcceptOfferDialog } from './accept-offer.dialog';
import { DeclineOfferDialog } from './decline-offer.dialog';
import { ShipmentBidSections } from './shipment-bid-sections';

@Component({
  selector: 'app-shipment-detail',
  imports: [HlmButtonImports, HlmSpinnerImports, NgIcon, RouterLink, ShipmentBidSections],
  viewProviders: [provideIcons({ lucideMoveLeft, lucideThumbsDown, lucideThumbsUp, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 rounded-md bg-card p-6">
      <header class="flex h-16 shrink-0 items-center justify-between border-b">
        <a hlmBtn variant="ghost" size="sm" routerLink=".." class="lg:hidden">
          <ng-icon name="lucideMoveLeft" />
          Back to shipments
        </a>

        <h1 class="hidden font-medium lg:flex">Shipment Offer {{ id() }}</h1>
        <div class="flex items-center gap-1">
          <button
            hlmBtn
            variant="ghost"
            size="icon"
            type="button"
            aria-label="Accept offer"
            [disabled]="detail() === null"
            (click)="openAcceptDialog()">
            <ng-icon name="lucideThumbsUp" />
          </button>
          <button
            hlmBtn
            variant="ghost"
            size="icon"
            type="button"
            aria-label="Decline offer"
            [disabled]="detail() === null"
            (click)="openDeclineDialog()">
            <ng-icon name="lucideThumbsDown" />
          </button>
          <a
            hlmBtn
            variant="ghost"
            size="icon"
            routerLink=".."
            class="hidden lg:inline-flex"
            aria-label="Back to shipments">
            <ng-icon name="lucideX" />
          </a>
        </div>
      </header>

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
            <div class="flex flex-1 flex-col gap-4 overflow-y-auto">
              <dl class="grid grid-cols-[150px_minmax(372px,1fr)_150px_minmax(372px,1fr)] gap-y-2 text-sm">
                <dt class="text-muted-foreground">Offer Status</dt>
                <dd>{{ detail.listing.status }}</dd>
                <dt class="text-muted-foreground">Offer Expiration Time</dt>
                <dd>{{ detail.bidDetail?.offerExpirationDisplay ?? '—' }}</dd>
                <dt class="text-muted-foreground">Shipment ID</dt>
                <dd>{{ detail.listing.shipmentId }}</dd>

                <dt class="text-muted-foreground">GBLOC</dt>
                <dd>{{ detail.listing.gbloc }}</dd>

                <dt class="text-muted-foreground">Requestor Name</dt>
                <dd>{{ detail.requestorName ?? '—' }}</dd>
                <dt class="text-muted-foreground">Requestor Email</dt>
                <dd>{{ detail.requestorEmail ?? '—' }}</dd>
                <dt class="text-muted-foreground">Requestor Phone</dt>
                <dd>{{ detail.bidDetail?.requestorPhone ?? '—' }}</dd>

                <dt class="text-muted-foreground">Origin</dt>
                <dd class="whitespace-pre-line">{{ detail.bidDetail?.originAddress ?? detail.listing.origin }}</dd>
                <dt class="text-muted-foreground">Destination</dt>
                <dd class="whitespace-pre-line">
                  {{ detail.bidDetail?.destinationAddress ?? detail.listing.destination }}
                </dd>
                <dt class="text-muted-foreground">Rank</dt>
                <dd>{{ detail.bidDetail?.bidRank ?? '—' }}</dd>

                <dt class="text-muted-foreground">Earliest Pickup</dt>
                <dd>{{ detail.bidDetail?.earliestPickupDisplay ?? detail.listing.pickupDate ?? '—' }}</dd>
                <dt class="text-muted-foreground">Latest Pickup</dt>
                <dd>{{ detail.bidDetail?.latestPickupDisplay ?? '—' }}</dd>
                <dt class="text-muted-foreground">Latest Delivery</dt>
                <dd>
                  {{ detail.bidDetail?.latestDeliveryDisplay ?? detail.listing.requiredDeliveryDate ?? '—' }}
                </dd>

                <dt class="text-muted-foreground">Quantity</dt>
                <dd>
                  @if (detail.bidDetail?.quantity !== null && detail.bidDetail?.quantity !== undefined) {
                    {{ detail.bidDetail?.quantity }} {{ detail.bidDetail?.quantityUom }}
                  } @else {
                    —
                  }
                </dd>
                <dt class="text-muted-foreground">Commodity Code</dt>
                <dd>{{ detail.bidDetail?.commodityCode ?? '—' }}</dd>
                <dt class="text-muted-foreground">Number Of Conveyances</dt>
                <dd>{{ detail.bidDetail?.numberOfConveyances ?? '—' }}</dd>

                <dt class="text-muted-foreground">Shipment Mode</dt>
                <dd>{{ detail.bidDetail?.shipmentMode ?? '—' }}</dd>

                <dt class="text-muted-foreground">Remarks</dt>
                <dd>{{ detail.bidDetail?.remarks ?? '—' }}</dd>
                <dt class="text-muted-foreground">SDG3 Remarks To Carrier</dt>
                <dd>{{ detail.bidDetail?.sdg3Remarks ?? '—' }}</dd>

                <dt class="text-muted-foreground">SCAC</dt>
                <dd>{{ detail.scac ?? '—' }}</dd>
                <dt class="text-muted-foreground">Tender</dt>
                <dd>{{ detail.tenderNumber ?? '—' }}</dd>
                <dt class="text-muted-foreground">Contract Number</dt>
                <dd>{{ detail.bidDetail?.contractNumber ?? '—' }}</dd>

                <dt class="text-muted-foreground">Phone Number</dt>
                <dd>{{ detail.bidDetail?.carrierPhone ?? '—' }}</dd>
                <dt class="text-muted-foreground">Effective Date</dt>
                <dd>{{ detail.bidDetail?.tenderEffectiveDate ?? '—' }}</dd>
                <dt class="text-muted-foreground">Expiration Date</dt>
                <dd>{{ detail.bidDetail?.tenderExpirationDate ?? '—' }}</dd>

                <dt class="text-muted-foreground">Rated Equipment</dt>
                <dd>{{ detail.equipmentDesc ?? '—' }}</dd>
                <dt class="text-muted-foreground">Rated Miles</dt>
                <dd>{{ detail.bidDetail?.ratedMiles ?? '—' }}</dd>
                <dt class="text-muted-foreground">Rate Qualifier</dt>
                <dd>{{ detail.bidDetail?.rateQualifier ?? '—' }}</dd>

                <dt class="text-muted-foreground">Rated Commodity</dt>
                <dd>{{ detail.bidDetail?.ratedCommodityCode ?? '—' }}</dd>
                <dt class="text-muted-foreground">Rate Used</dt>
                <dd>{{ detail.rateUsed ?? '—' }}</dd>
                <dt class="text-muted-foreground">Rated Quantity Limits</dt>
                <dd>{{ detail.bidDetail?.ratedQuantityLimits ?? '—' }}</dd>

                <dt class="text-muted-foreground">Line Haul Cost</dt>
                <dd>{{ detail.lineHaulCost ?? '—' }}</dd>
                <dt class="text-muted-foreground">Service Cost</dt>
                <dd>{{ detail.bidDetail?.serviceCost ?? '—' }}</dd>
                <dt class="text-muted-foreground">Misc. Cost</dt>
                <dd>{{ detail.bidDetail?.miscCost ?? '—' }}</dd>

                <dt class="text-muted-foreground">Fuel Adjustment</dt>
                <dd>{{ detail.bidDetail?.fuelAdjustment ?? '—' }}</dd>
                <dt class="text-muted-foreground">Total Cost</dt>
                <dd>{{ detail.totalAmount ?? '—' }}</dd>
              </dl>

              <app-shipment-bid-sections [bidDetail]="detail.bidDetail" />
            </div>
          }
        }
      }
    </div>
  `,
})
export class ShipmentDetailPage {
  private readonly store: ShipmentsStoreType = inject(ShipmentsStore);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly dialogService: HlmDialogService = inject(HlmDialogService);

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

  protected openAcceptDialog(): void {
    const detail = this.detail();
    if (!detail) {
      return;
    }

    this.dialogService.open(AcceptOfferDialog, {
      contentClass: 'min-w-md',
      context: {
        offerId: detail.listing.offerId,
        conveyancesAvailable: detail.bidDetail?.numberOfConveyances ?? 0,
        store: this.store,
      },
    });
  }

  protected openDeclineDialog(): void {
    const detail = this.detail();
    if (!detail) {
      return;
    }

    this.dialogService.open(DeclineOfferDialog, {
      context: { offerId: detail.listing.offerId, store: this.store },
    });
  }
}
