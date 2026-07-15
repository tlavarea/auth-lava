import { Component, input, InputSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmItemImports } from '@spartan-ng/helm/item';

import { ShipmentListingRow } from '../shipments.models';

@Component({
  selector: 'app-shipment-item',
  imports: [RouterLink, HlmBadgeImports, HlmItemImports],
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
            <div hlmItemTitle class="flex items-center gap-2">
              {{ shipment.shipmentId }}
              <span hlmBadge [variant]="statusVariant(shipment.status)">{{ shipment.status }}</span>
            </div>
            <div hlmItemDescription>{{ shipment.origin }} &rarr; {{ shipment.destination }}</div>
          </div>
        </a>
      }
    </div>
  `,
})
export class ShipmentItem {
  readonly shipments: InputSignal<ShipmentListingRow[]> = input.required<ShipmentListingRow[]>();
  readonly selectedId: InputSignal<number | null> = input<number | null>(null);

  protected statusVariant(status: string): 'default' | 'secondary' | 'destructive' {
    switch (status.toUpperCase()) {
      case 'ACCEPTED':
        return 'default';
      case 'EXPIRED':
        return 'destructive';
      default:
        return 'secondary';
    }
  }
}
