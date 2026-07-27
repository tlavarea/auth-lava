import { Component, input, InputSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmItemImports } from '@spartan-ng/helm/item';

import { truckStatusBadge } from '../truck-status';
import { TruckListingRow } from '../trucks.models';

@Component({
  selector: 'app-truck-item',
  imports: [RouterLink, HlmBadgeImports, HlmItemImports],
  template: `
    <div hlmItemGroup class="flex flex-col gap-4">
      @for (truck of trucks(); track truck.id) {
        @let isSelected = truck.id === selectedId();
        <a
          hlmItem
          variant="outline"
          [routerLink]="[truck.id]"
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
              <span class="truncate">{{ truck.truckNumber }}</span>
              @let badge = truckStatusBadge(truck.engineState, truck.ecuSpeedMph);
              <span hlmBadge [variant]="badge.variant" [class]="badge.class">{{ badge.label }}</span>
            </div>
            <div hlmItemDescription class="truncate">
              {{ truck.currentDriverName ?? 'No driver assigned' }}
            </div>
          </div>
        </a>
      }
    </div>
  `,
})
export class TruckItem {
  readonly trucks: InputSignal<TruckListingRow[]> = input.required<TruckListingRow[]>();
  readonly selectedId: InputSignal<string | null> = input<string | null>(null);

  protected readonly truckStatusBadge = truckStatusBadge;
}
