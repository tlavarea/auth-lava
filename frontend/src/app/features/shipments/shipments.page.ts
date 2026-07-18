import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, OnInit, Signal, signal, WritableSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { ShipmentDetailPage } from './shipment-detail/shipment-detail.page';
import { ShipmentItem } from './shipment-item/shipment-item';
import { ShipmentTable } from './shipment-table/shipment-table';
import { SortOption, sortShipments } from './shipment-table/shipment-table.filters';
import { ShipmentListingRow } from './shipments.models';
import { ShipmentsRequestStatus, ShipmentsStore, ShipmentsStoreType } from './shipments.store';

// Matches Tailwind's `lg` breakpoint used for the JS-driven table/item mount decision below.
const DESKTOP_QUERY = '(min-width: 1024px)';

@Component({
  selector: 'app-shipments',
  imports: [HlmSpinnerImports, RouterOutlet, ShipmentItem, ShipmentTable],
  providers: [ShipmentsStore],
  template: `
    <div class="relative flex h-full">
      <section [class]="masterClasses()" [attr.inert]="detailOpen() && !isDesktop() ? '' : null">
        @switch (listStatus()) {
          @case ('loading') {
            <div class="flex h-full items-center justify-center">
              <hlm-spinner />
            </div>
          }
          @case ('error') {
            <p class="text-destructive">Couldn't load shipments.</p>
          }
          @default {
            @if (isDesktop() && !detailOpen()) {
              <app-shipment-table [shipments]="shipments()" [(sortOption)]="sortOption" />
            } @else {
              <app-shipment-item [shipments]="sortedShipments()" [selectedId]="selectedId()" />
            }
          }
        }
      </section>

      @if (detailOpen() && !isDesktop()) {
        <button
          type="button"
          class="fixed inset-0 z-40 bg-black/50"
          aria-label="Close shipment details"
          (click)="closeDetail()"></button>
      }

      <div [class]="panelClasses()" [attr.inert]="!detailOpen() && !isDesktop() ? '' : null">
        <router-outlet (activate)="activeDetail.set($event)" (deactivate)="activeDetail.set(null)" />
      </div>
    </div>
  `,
})
export class ShipmentsPage implements OnInit {
  private readonly store: ShipmentsStoreType = inject(ShipmentsStore);
  private readonly breakpointObserver: BreakpointObserver = inject(BreakpointObserver);
  private readonly router: Router = inject(Router);

  protected readonly shipments: Signal<ShipmentListingRow[]> = this.store.shipments;
  protected readonly listStatus: Signal<ShipmentsRequestStatus> = this.store.listStatus;

  // Owned here (rather than locally in ShipmentTable) so ShipmentItem can render shipments in the same
  // order when it swaps in for the table on mobile or while a detail is open.
  protected readonly sortOption: WritableSignal<SortOption> = signal<SortOption>('rank-asc');
  protected readonly sortedShipments: Signal<ShipmentListingRow[]> = computed(() =>
    sortShipments(this.shipments(), this.sortOption())
  );

  protected readonly activeDetail: WritableSignal<ShipmentDetailPage | null> = signal(null);
  protected readonly detailOpen: Signal<boolean> = computed(() => this.activeDetail() !== null);
  protected readonly selectedId: Signal<number | null> = computed(() => {
    const id = this.activeDetail()?.id();
    return id !== undefined ? Number(id) : null;
  });

  protected readonly isDesktop: Signal<boolean> = toSignal(
    this.breakpointObserver.observe(DESKTOP_QUERY).pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_QUERY) }
  );

  // On desktop, an open detail shrinks the master column to an item-view sidebar; closed, it's full width.
  // Scrolling is frozen while a detail is open so the selected row can never be scrolled out of view.
  // Desktop-with-no-detail renders ShipmentTable, which manages its own internal scroll region (toolbar
  // and pagination stay fixed, only the card list scrolls) so the section itself must not scroll there.
  // Mobile keeps document-style scrolling since ShipmentItem has no sticky toolbar of its own.
  protected readonly masterClasses: Signal<string> = computed(() => {
    if (!this.isDesktop()) {
      return 'h-full w-full overflow-y-auto p-4 [scrollbar-width:none]';
    }
    return this.detailOpen()
      ? 'h-full w-full max-w-md shrink-0 overflow-hidden bg-muted/20 p-4 [scrollbar-width:none]'
      : 'h-full w-full overflow-hidden p-4 [scrollbar-width:none]';
  });

  // Mobile keeps the slide-over sheet; desktop is an inline split pane with no overlay.
  protected readonly panelClasses: Signal<string> = computed(() => {
    if (!this.isDesktop()) {
      const base =
        'fixed inset-y-0 end-0 z-50 flex w-full flex-col overflow-y-auto border-s border-border ' +
        'bg-popover text-popover-foreground shadow-lg transition-transform duration-200 ease-in-out sm:max-w-xl';
      return this.detailOpen() ? base : `${base} translate-x-full`;
    }
    return this.detailOpen()
      ? 'flex-1 h-full overflow-y-auto bg-accent p-6 shadow-md shadow-neutral-400 dark:shadow-neutral-700'
      : 'hidden';
  });

  ngOnInit(): void {
    void this.store.loadShipments();
  }

  protected closeDetail(): void {
    void this.router.navigate(['/shipments']);
  }
}
