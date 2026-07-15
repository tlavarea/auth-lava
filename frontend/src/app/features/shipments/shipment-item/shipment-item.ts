import { Component, input, InputSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmItemImports } from '@spartan-ng/helm/item';

import { ShipmentListingRow } from '../shipments.models';

@Component({
  selector: 'app-shipment-item',
  imports: [RouterLink, HlmBadgeImports, HlmButtonImports, HlmItemImports],
  template: `
    <div hlmItemGroup class="flex flex-col gap-2">
      @for (shipment of shipments(); track shipment.offerId) {
        <div hlmItem variant="outline">
          <div hlmItemContent>
            <div hlmItemTitle class="flex items-center gap-2">
              {{ shipment.shipmentId }}
              <span hlmBadge [variant]="statusVariant(shipment.status)">{{ shipment.status }}</span>
            </div>
            <div hlmItemDescription>{{ shipment.origin }} &rarr; {{ shipment.destination }}</div>
          </div>
          <div hlmItemActions>
            <a hlmBtn variant="outline" size="sm" [routerLink]="[shipment.offerId]">View details</a>
          </div>
        </div>
      }
    </div>
  `,
})
export class ShipmentItem {
  readonly shipments: InputSignal<ShipmentListingRow[]> = input.required<ShipmentListingRow[]>();

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
