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
            <th hlmTh>Shipment</th>
            <th hlmTh>Status</th>
            <th hlmTh>Origin</th>
            <th hlmTh>Destination</th>
            <th hlmTh>Pickup</th>
            <th hlmTh>Required delivery</th>
          </tr>
        </thead>
        <tbody hlmTBody>
          @for (shipment of shipments(); track shipment.offerId) {
            <tr hlmTr class="cursor-pointer" [routerLink]="[shipment.offerId]">
              <td hlmTd>{{ shipment.shipmentId }}</td>
              <td hlmTd>
                <span hlmBadge [variant]="statusVariant(shipment.status)">{{ shipment.status }}</span>
              </td>
              <td hlmTd>{{ shipment.origin }}</td>
              <td hlmTd>{{ shipment.destination }}</td>
              <td hlmTd>{{ shipment.pickupDate }}</td>
              <td hlmTd>{{ shipment.requiredDeliveryDate }}</td>
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
