import { Component, input, InputSignal } from '@angular/core';

import { HlmAccordionImports } from '@spartan-ng/helm/accordion';
import { HlmTableImports } from '@spartan-ng/helm/table';

import { GfmBidDetail, ShipperRequestedService } from '../shipments.models';

@Component({
  selector: 'app-shipment-bid-sections',
  imports: [HlmAccordionImports, HlmTableImports],
  template: `
    <hlm-accordion type="multiple">
      <hlm-accordion-item>
        <hlm-accordion-trigger>Shipper Requested Services</hlm-accordion-trigger>
        <hlm-accordion-content>
          @if (bidDetail()?.shipperRequestedServices?.length) {
            <div hlmTableContainer>
              <table hlmTable>
                <thead hlmTHead>
                  <tr hlmTr>
                    <th hlmTh>Description</th>
                    <th hlmTh>Cost</th>
                  </tr>
                </thead>
                <tbody hlmTBody>
                  @for (service of bidDetail()?.shipperRequestedServices; track $index) {
                    <tr hlmTr>
                      <td hlmTd>{{ serviceDescription(service) }}</td>
                      <td hlmTd>{{ service.cost ?? '—' }}</td>
                    </tr>
                    @for (param of service.params; track $index) {
                      <tr hlmTr>
                        <td hlmTd colspan="2" class="ps-6 text-muted-foreground">{{ paramSummary(param) }}</td>
                      </tr>
                    }
                  }
                </tbody>
              </table>
            </div>
            <p class="mt-2 text-end text-sm font-medium">
              Shipper Requested Services Cost: {{ bidDetail()?.serviceCost ?? '—' }}
            </p>
          } @else {
            <p class="text-sm text-muted-foreground">No requested services.</p>
          }
        </hlm-accordion-content>
      </hlm-accordion-item>

      <hlm-accordion-item>
        <hlm-accordion-trigger>Shipment Details</hlm-accordion-trigger>
        <hlm-accordion-content>
          @if (bidDetail()?.equipmentUnits?.length) {
            @for (unit of bidDetail()?.equipmentUnits; track $index) {
              <div class="mb-4">
                <p class="mb-2 text-sm font-medium">
                  SRC/CIIC: {{ unit.ciic ?? '—' }} / Commodity Code: {{ unit.commodityCode ?? '—' }} / Description:
                  {{ unit.commodityDesc ?? '—' }} / NSN: {{ unit.nsn ?? '—' }}
                </p>
                <div hlmTableContainer>
                  <table hlmTable>
                    <thead hlmTHead>
                      <tr hlmTr>
                        <th hlmTh>Description</th>
                        <th hlmTh>Type</th>
                        <th hlmTh>Pieces</th>
                        <th hlmTh>Length (inches)</th>
                        <th hlmTh>Width (inches)</th>
                        <th hlmTh>Height (inches)</th>
                        <th hlmTh>Cubic Feet</th>
                        <th hlmTh>Quantity</th>
                      </tr>
                    </thead>
                    <tbody hlmTBody>
                      @for (item of unit.items; track $index) {
                        <tr hlmTr>
                          <td hlmTd>{{ item.description ?? '—' }}</td>
                          <td hlmTd>{{ item.packType ?? '—' }}</td>
                          <td hlmTd>{{ item.pieces ?? '—' }}</td>
                          <td hlmTd>{{ item.length ?? '—' }}</td>
                          <td hlmTd>{{ item.width ?? '—' }}</td>
                          <td hlmTd>{{ item.height ?? '—' }}</td>
                          <td hlmTd>{{ item.cubicFeet ?? '—' }}</td>
                          <td hlmTd>
                            @if (item.quantity !== null && item.quantity !== undefined) {
                              {{ item.quantity }} {{ item.quantityUom }}
                            } @else {
                              —
                            }
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </div>
            }
          } @else {
            <p class="text-sm text-muted-foreground">No shipment details.</p>
          }
        </hlm-accordion-content>
      </hlm-accordion-item>

      <hlm-accordion-item>
        <hlm-accordion-trigger>RINs</hlm-accordion-trigger>
        <hlm-accordion-content>
          <p class="text-sm">{{ bidDetail()?.rins ?? '—' }}</p>
        </hlm-accordion-content>
      </hlm-accordion-item>
    </hlm-accordion>
  `,
})
export class ShipmentBidSections {
  readonly bidDetail: InputSignal<GfmBidDetail | null> = input.required<GfmBidDetail | null>();

  protected serviceDescription(service: ShipperRequestedService): string {
    return service.description ? `${service.description} (${service.code ?? ''})` : `(${service.code ?? ''})`;
  }

  protected paramSummary(param: Record<string, unknown>): string {
    return Object.values(param)
      .filter((value) => value !== null && value !== undefined)
      .join(' ');
  }
}
