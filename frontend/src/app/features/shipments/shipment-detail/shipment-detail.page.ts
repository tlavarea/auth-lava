import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  InputSignal,
  Signal,
  signal,
  WritableSignal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoveLeft, lucideX } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';
import { HlmToggleGroupImports } from '@spartan-ng/helm/toggle-group';

import { OfferResponseType, ShipmentDetailResponse } from '../shipments.models';
import { ShipmentsRequestStatus, ShipmentsStore, ShipmentsStoreType } from '../shipments.store';
import { ShipmentBidSections } from './shipment-bid-sections';

@Component({
  selector: 'app-shipment-detail',
  imports: [
    HlmAlertImports,
    HlmButtonImports,
    HlmFieldImports,
    HlmInputImports,
    HlmSpinnerImports,
    HlmToggleGroupImports,
    NgIcon,
    RouterLink,
    ShipmentBidSections,
  ],
  viewProviders: [provideIcons({ lucideMoveLeft, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4 rounded-md bg-card p-6">
      <header class="flex h-16 shrink-0 items-center justify-between border-b">
        <a hlmBtn variant="ghost" size="sm" routerLink=".." class="lg:hidden">
          <ng-icon name="lucideMoveLeft" />
          Back to shipments
        </a>

        <h1 class="hidden font-medium lg:flex">Shipment Offer {{ id() }}</h1>
        <a
          hlmBtn
          variant="ghost"
          size="icon"
          routerLink=".."
          class="hidden lg:inline-flex"
          aria-label="Back to shipments">
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

              <div class="flex flex-col gap-4 rounded-md border border-border p-4">
                <div class="flex flex-wrap items-end gap-4">
                  <div hlmField>
                    <span hlmFieldLabel id="offer-response-label">Offer Response</span>
                    <hlm-toggle-group
                      type="single"
                      variant="outline"
                      aria-labelledby="offer-response-label"
                      [value]="offerResponseChoice()"
                      (valueChange)="offerResponseChoice.set($any($event))">
                      <button hlmToggleGroupItem type="button" value="ACCEPT">Accept</button>
                      <button hlmToggleGroupItem type="button" value="DECLINE">Decline</button>
                    </hlm-toggle-group>
                    <hlm-field-error [forceShow]="offerResponseChoice() === null">
                      No offer response selected.
                    </hlm-field-error>
                  </div>

                  <div hlmField class="w-40">
                    <label hlmFieldLabel for="conveyances-available">Conveyances Available</label>
                    <input
                      hlmInput
                      id="conveyances-available"
                      type="number"
                      min="0"
                      [value]="conveyancesAvailable()"
                      (input)="onConveyancesInput($event)" />
                  </div>

                  <button
                    hlmBtn
                    type="button"
                    [disabled]="!canSubmitOfferResponse()"
                    (click)="onSubmitOfferResponse(detail.listing.offerId)">
                    @if (submitting()) {
                      <hlm-spinner />
                      Submitting...
                    } @else {
                      Submit
                    }
                  </button>
                  <a hlmBtn variant="outline" routerLink="..">Close</a>
                </div>

                @if (submitError(); as submitError) {
                  <div hlmAlert variant="destructive">
                    <p hlmAlertDescription>{{ submitError }}</p>
                  </div>
                }
              </div>
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

  // Bound from the `:id` route param by withComponentInputBinding() in app.config.ts.
  readonly id: InputSignal<string> = input.required<string>();

  protected readonly detail: Signal<ShipmentDetailResponse | null> = this.store.selectedDetail;
  protected readonly status: Signal<ShipmentsRequestStatus> = this.store.detailStatus;

  protected readonly offerResponseChoice: WritableSignal<OfferResponseType | null> = signal(null);
  protected readonly conveyancesAvailable: WritableSignal<number> = signal(0);
  protected readonly submitError: WritableSignal<string | null> = signal(null);

  protected readonly submitting: Signal<boolean> = computed(() => this.store.respondStatus() === 'loading');
  protected readonly canSubmitOfferResponse: Signal<boolean> = computed(
    () => this.offerResponseChoice() !== null && !this.submitting()
  );

  constructor() {
    effect(() => {
      const offerId = Number(this.id());
      if (!Number.isNaN(offerId)) {
        void this.store.loadShipmentDetail(offerId);
      }
    });

    this.destroyRef.onDestroy(() => this.store.clearSelectedDetail());
  }

  protected onConveyancesInput(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.conveyancesAvailable.set(Number.isNaN(value) ? 0 : value);
  }

  protected async onSubmitOfferResponse(offerId: number): Promise<void> {
    const response = this.offerResponseChoice();
    if (response === null) {
      return;
    }

    this.submitError.set(null);
    try {
      await this.store.respondToOffer(offerId, {
        response,
        conveyancesAvailable: this.conveyancesAvailable(),
      });
    } catch (error) {
      this.submitError.set(this.extractSubmitErrorMessage(error));
    }
  }

  private extractSubmitErrorMessage(error: unknown): string {
    if (error && typeof error === 'object' && 'status' in error && (error as { status: number }).status === 501) {
      return "This feature isn't available yet.";
    }
    return 'Something went wrong submitting your response. Please try again.';
  }
}
