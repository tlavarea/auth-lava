import { Component, computed, effect, input, InputSignal, Signal, signal, WritableSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideSearch, lucideX } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmInputGroupImports } from '@spartan-ng/helm/input-group';
import { HlmItemImports } from '@spartan-ng/helm/item';
import { HlmPaginationImports } from '@spartan-ng/helm/pagination';
import { HlmSelectImports } from '@spartan-ng/helm/select';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '../driver-status';
import { DriverListingRow } from '../drivers.models';
import {
  ALL,
  DriverFilters,
  filterDrivers,
  normalizedDutyStatus,
  paginateDrivers,
  SORT_OPTION_LABELS,
  sortDrivers,
  SortOption,
  UNKNOWN_DUTY_STATUS,
} from './driver-table.filters';

type AppliedFilter = { label: string; clear: () => void };

@Component({
  selector: 'app-driver-table',
  imports: [
    RouterLink,
    NgIcon,
    HlmBadgeImports,
    HlmInputGroupImports,
    HlmItemImports,
    HlmPaginationImports,
    HlmSelectImports,
  ],
  viewProviders: [provideIcons({ lucideSearch, lucideX })],
  template: `
    <div class="flex h-full flex-col gap-4">
      <div class="flex shrink-0 flex-col gap-4">
        <div class="flex flex-wrap items-center gap-3">
          <div hlmInputGroup class="w-full max-w-xs">
            <div hlmInputGroupAddon>
              <ng-icon name="lucideSearch" />
            </div>
            <input
              hlmInputGroupInput
              type="search"
              placeholder="Search drivers"
              aria-label="Search drivers"
              [value]="searchText()"
              (input)="searchText.set($any($event.target).value)" />
          </div>

          <hlm-select [itemToString]="statusItemToString" [(value)]="statusFilter">
            <hlm-select-trigger class="w-44" aria-label="Filter by driving status">
              <hlm-select-value />
            </hlm-select-trigger>
            <hlm-select-content *hlmSelectPortal>
              <hlm-select-group>
                <hlm-select-item [value]="ALL">All statuses</hlm-select-item>
                @for (status of statusOptions(); track status) {
                  <hlm-select-item [value]="status">{{ statusItemToString(status) }}</hlm-select-item>
                }
              </hlm-select-group>
            </hlm-select-content>
          </hlm-select>

          <hlm-select class="ml-auto" [itemToString]="sortItemToString" [(value)]="sortOption">
            <hlm-select-trigger class="w-56" aria-label="Sort by">
              <hlm-select-value />
            </hlm-select-trigger>
            <hlm-select-content *hlmSelectPortal>
              <hlm-select-group>
                @for (option of sortOptions; track option.value) {
                  <hlm-select-item [value]="option.value">{{ option.label }}</hlm-select-item>
                }
              </hlm-select-group>
            </hlm-select-content>
          </hlm-select>
        </div>

        @if (appliedFilters().length > 0) {
          <div class="flex flex-wrap items-center gap-2">
            @for (chip of appliedFilters(); track chip.label) {
              <span hlmBadge variant="outline" class="gap-1">
                {{ chip.label }}
                <button type="button" [attr.aria-label]="'Remove filter: ' + chip.label" (click)="chip.clear()">
                  <ng-icon name="lucideX" />
                </button>
              </span>
            }
            <button
              type="button"
              class="text-sm text-primary underline-offset-4 hover:underline"
              (click)="resetFilters()">
              Reset filters
            </button>
          </div>
        }
      </div>

      <div class="min-h-0 flex-1 [scrollbar-width:none] overflow-y-auto">
        @if (pagedDrivers().length === 0) {
          <div
            class="flex flex-col items-center gap-2 rounded-xl border border-dashed p-10 text-center text-muted-foreground">
            <p>No drivers match your filters.</p>
            @if (appliedFilters().length > 0) {
              <button
                type="button"
                class="text-sm text-primary underline-offset-4 hover:underline"
                (click)="resetFilters()">
                Clear filters
              </button>
            }
          </div>
        } @else {
          <div hlmItemGroup class="flex flex-col gap-3">
            @for (driver of pagedDrivers(); track driver.id) {
              <a hlmItem variant="outline" class="rounded-xl p-4 hover:bg-muted/50" [routerLink]="[driver.id]">
                <dl class="grid w-full grid-cols-4 gap-x-6 gap-y-4 text-sm">
                  <div>
                    <dt class="text-xs text-muted-foreground">Name</dt>
                    <dd class="font-semibold">{{ driver.name }}</dd>
                  </div>
                  <div>
                    <dt class="text-xs text-muted-foreground">Driving Status</dt>
                    <dd>
                      <span hlmBadge [variant]="driverDutyStatusVariant(driver.dutyStatus)">
                        {{ driverDutyStatusLabel(driver.dutyStatus) }}
                      </span>
                    </dd>
                  </div>
                  <div>
                    <dt class="text-xs text-muted-foreground">Current vehicle</dt>
                    <dd>{{ driver.currentVehicleName ?? '—' }}</dd>
                  </div>
                  <div>
                    <dt class="text-xs text-muted-foreground">Current Location</dt>
                    <dd>{{ driver.currentLocation ?? '—' }}</dd>
                  </div>
                </dl>
              </a>
            }
          </div>
        }
      </div>

      @if (totalItems() > 0) {
        <hlm-numbered-pagination
          class="shrink-0"
          [totalItems]="totalItems()"
          [pageSizes]="[10, 20, 50]"
          [(currentPage)]="currentPage"
          [(itemsPerPage)]="itemsPerPage" />
      }
    </div>
  `,
})
export class DriverTable {
  readonly drivers: InputSignal<DriverListingRow[]> = input.required<DriverListingRow[]>();

  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;
  protected readonly ALL = ALL;
  protected readonly sortOptions: { value: SortOption; label: string }[] = Object.entries(SORT_OPTION_LABELS).map(
    ([value, label]) => ({ value: value as SortOption, label })
  );

  protected readonly statusItemToString = (value: unknown): string =>
    value === ALL ? 'All statuses' : driverDutyStatusLabel(value === UNKNOWN_DUTY_STATUS ? null : String(value));
  protected readonly sortItemToString = (value: unknown): string =>
    SORT_OPTION_LABELS[value as SortOption] ?? String(value);

  protected readonly searchText: WritableSignal<string> = signal('');
  protected readonly statusFilter: WritableSignal<string> = signal(ALL);
  protected readonly sortOption: WritableSignal<SortOption> = signal('name-asc');
  protected readonly currentPage: WritableSignal<number> = signal(1);
  protected readonly itemsPerPage: WritableSignal<number> = signal(10);

  protected readonly statusOptions: Signal<string[]> = computed(() =>
    Array.from(new Set(this.drivers().map((driver) => normalizedDutyStatus(driver.dutyStatus)))).sort()
  );

  protected readonly filteredDrivers: Signal<DriverListingRow[]> = computed(() => {
    const filters: DriverFilters = { searchText: this.searchText(), dutyStatus: this.statusFilter() };
    return filterDrivers(this.drivers(), filters);
  });

  protected readonly sortedDrivers: Signal<DriverListingRow[]> = computed(() =>
    sortDrivers(this.filteredDrivers(), this.sortOption())
  );

  protected readonly totalItems: Signal<number> = computed(() => this.sortedDrivers().length);

  protected readonly pagedDrivers: Signal<DriverListingRow[]> = computed(() =>
    paginateDrivers(this.sortedDrivers(), this.currentPage(), this.itemsPerPage())
  );

  protected readonly appliedFilters: Signal<AppliedFilter[]> = computed(() => {
    const chips: AppliedFilter[] = [];
    const query = this.searchText().trim();
    if (query !== '') {
      chips.push({ label: `Search: "${query}"`, clear: () => this.searchText.set('') });
    }
    if (this.statusFilter() !== ALL) {
      chips.push({
        label: `Driving Status: ${this.statusItemToString(this.statusFilter())}`,
        clear: () => this.statusFilter.set(ALL),
      });
    }
    return chips;
  });

  constructor() {
    effect(() => {
      this.searchText();
      this.statusFilter();
      this.sortOption();
      this.currentPage.set(1);
    });
  }

  protected resetFilters(): void {
    this.searchText.set('');
    this.statusFilter.set(ALL);
  }
}
