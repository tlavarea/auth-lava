import { Component, input, InputSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmItemImports } from '@spartan-ng/helm/item';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '../driver-status';
import { DriverListingRow } from '../drivers.models';

@Component({
  selector: 'app-driver-item',
  imports: [RouterLink, HlmBadgeImports, HlmItemImports],
  template: `
    <div hlmItemGroup class="flex flex-col gap-4">
      @for (driver of drivers(); track driver.id) {
        @let isSelected = driver.id === selectedId();
        <a
          hlmItem
          variant="outline"
          [routerLink]="[driver.id]"
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
              <span class="truncate">{{ driver.name }}</span>
              <span hlmBadge [variant]="driverDutyStatusVariant(driver.dutyStatus)">
                {{ driverDutyStatusLabel(driver.dutyStatus) }}
              </span>
            </div>
            <div hlmItemDescription class="truncate">
              {{ driver.currentVehicleName ?? 'No vehicle assigned' }}
            </div>
          </div>
        </a>
      }
    </div>
  `,
})
export class DriverItem {
  readonly drivers: InputSignal<DriverListingRow[]> = input.required<DriverListingRow[]>();
  readonly selectedId: InputSignal<string | null> = input<string | null>(null);

  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;
}
