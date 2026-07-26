import { Component, input, InputSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmItemImports } from '@spartan-ng/helm/item';

import { TrailerListingRow } from '../trailers.models';

@Component({
  selector: 'app-trailer-item',
  imports: [RouterLink, HlmItemImports],
  template: `
    <div hlmItemGroup class="flex flex-col gap-4">
      @for (trailer of trailers(); track trailer.id) {
        @let isSelected = trailer.id === selectedId();
        <a
          hlmItem
          variant="outline"
          [routerLink]="[trailer.id]"
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
            <div hlmItemTitle class="truncate">{{ trailer.label }}</div>
            <div hlmItemDescription class="truncate">
              {{ trailer.currentTruckNumber ? 'On ' + trailer.currentTruckNumber : 'No truck assigned' }}
            </div>
          </div>
        </a>
      }
    </div>
  `,
})
export class TrailerItem {
  readonly trailers: InputSignal<TrailerListingRow[]> = input.required<TrailerListingRow[]>();
  readonly selectedId: InputSignal<string | null> = input<string | null>(null);
}
