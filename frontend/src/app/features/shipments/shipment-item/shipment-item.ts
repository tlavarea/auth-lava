import { Component, input, InputSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowRight } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmItemImports } from '@spartan-ng/helm/item';

import { shipmentLocationState } from '../shipment-location';
import { shipmentRankVariant } from '../shipment-rank';
import { shipmentStatusVariant } from '../shipment-status';
import { ShipmentListingRow } from '../shipments.models';

@Component({
  selector: 'app-shipment-item',
  imports: [RouterLink, NgIcon, HlmBadgeImports, HlmItemImports],
  viewProviders: [provideIcons({ lucideArrowRight })],
  template: `
    <div hlmItemGroup class="flex flex-col gap-4">
      @for (shipment of shipments(); track shipment.offerId) {
        @let isSelected = shipment.offerId === selectedId();
        <a
          hlmItem
          variant="outline"
          [routerLink]="[shipment.offerId]"
          [attr.aria-current]="isSelected ? 'page' : null"
          [class]="{
            'bg-accent': isSelected,
            'w-[calc(100%+1rem)]': isSelected,
            'rounded-s-md': isSelected,
            'rounded-e-none': isSelected,
            'border-e-0': isSelected,
            'shadow-sm': isSelected,
            relative: isSelected,
            'hover:bg-muted/50': !isSelected,
          }">
          <div hlmItemContent>
            <div hlmItemTitle class="flex flex-nowrap items-center gap-2">
              <span class="whitespace-nowrap">
                {{ locationState(shipment.origin) }}
                <ng-icon name="lucideArrowRight" />
                {{ locationState(shipment.destination) }}
              </span>
              <span hlmBadge [variant]="shipmentRankVariant(shipment.rank)">Rank {{ shipment.rank }}</span>
              <span hlmBadge [variant]="shipmentStatusVariant(shipment.status)">{{ shipment.status }}</span>
            </div>
            <div hlmItemDescription class="truncate">
              Pickup {{ shipment.pickupDate ?? '—' }} &middot; Required
              {{ shipment.requiredDeliveryDate ?? '—' }} &middot; {{ shipment.equipType }}
            </div>
          </div>
        </a>
      }
    </div>
  `,
})
export class ShipmentItem {
  readonly shipments: InputSignal<ShipmentListingRow[]> = input.required<ShipmentListingRow[]>();
  readonly selectedId: InputSignal<number | null> = input<number | null>(null);

  protected readonly shipmentStatusVariant = shipmentStatusVariant;
  protected readonly shipmentRankVariant = shipmentRankVariant;
  protected readonly locationState = shipmentLocationState;
}
