import { Component, input, InputSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmTableImports } from '@spartan-ng/helm/table';

import { ShipmentListingRow } from '../shipments.models';

@Component({
  selector: 'app-shipment-table',
  imports: [RouterLink, HlmBadgeImports, HlmTableImports],
  template: `
    <div hlmTableContainer>
      <table hlmTable>
        <thead hlmTHead>
          <tr hlmTr>
            <th hlmTh class="px-3">Shipment</th>
            <th hlmTh class="px-3">Status</th>
            <th hlmTh class="px-3">Origin</th>
            <th hlmTh class="px-3">Destination</th>
            <th hlmTh class="px-3">Pickup</th>
            <th hlmTh class="px-3">Required delivery</th>
          </tr>
        </thead>
        <tbody hlmTBody>
          @for (shipment of shipments(); track shipment.offerId) {
            <tr hlmTr class="cursor-pointer" [routerLink]="[shipment.offerId]">
              <td hlmTd class="px-3 py-3">{{ shipment.shipmentId }}</td>
              <td hlmTd class="px-3 py-3">
                <span hlmBadge [variant]="statusVariant(shipment.status)">{{ shipment.status }}</span>
              </td>
              <td hlmTd class="px-3 py-3">{{ shipment.origin }}</td>
              <td hlmTd class="px-3 py-3">{{ shipment.destination }}</td>
              <td hlmTd class="px-3 py-3">{{ shipment.pickupDate }}</td>
              <td hlmTd class="px-3 py-3">{{ shipment.requiredDeliveryDate }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class ShipmentTable {
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
