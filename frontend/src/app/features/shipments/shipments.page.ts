import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, inject, OnInit, Signal, signal, WritableSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { ShipmentItem } from './shipment-item/shipment-item';
import { ShipmentTable } from './shipment-table/shipment-table';
import { ShipmentListingRow } from './shipments.models';
import { ShipmentsRequestStatus, ShipmentsStore, ShipmentsStoreType } from './shipments.store';

// Matches Tailwind's `lg` breakpoint used for the JS-driven table/item mount decision below.
const DESKTOP_QUERY = '(min-width: 1024px)';

@Component({
  selector: 'app-shipments',
  imports: [HlmSpinnerImports, RouterOutlet, ShipmentItem, ShipmentTable],
  providers: [ShipmentsStore],
  template: `
    <div class="relative h-full">
      <section class="h-full overflow-y-auto p-4">
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
            @if (isDesktop()) {
              <app-shipment-table [shipments]="shipments()" />
            } @else {
              <app-shipment-item [shipments]="shipments()" />
            }
          }
        }
      </section>

      @if (detailOpen()) {
        <button
          type="button"
          class="fixed inset-0 z-40 bg-black/50"
          aria-label="Close shipment details"
          (click)="closeDetail()"></button>
      }

      <div [class]="panelClasses" [class.translate-x-full]="!detailOpen()" [attr.inert]="detailOpen() ? null : ''">
        <router-outlet (activate)="detailOpen.set(true)" (deactivate)="detailOpen.set(false)" />
      </div>
    </div>
  `,
})
export class ShipmentsPage implements OnInit {
  private readonly store: ShipmentsStoreType = inject(ShipmentsStore);
  private readonly breakpointObserver: BreakpointObserver = inject(BreakpointObserver);
  private readonly router: Router = inject(Router);

  protected readonly panelClasses =
    'fixed inset-y-0 end-0 z-50 flex w-full flex-col overflow-y-auto border-s border-border ' +
    'bg-popover text-popover-foreground shadow-lg transition-transform duration-200 ease-in-out sm:max-w-xl';

  protected readonly shipments: Signal<ShipmentListingRow[]> = this.store.shipments;
  protected readonly listStatus: Signal<ShipmentsRequestStatus> = this.store.listStatus;
  protected readonly detailOpen: WritableSignal<boolean> = signal(false);

  protected readonly isDesktop: Signal<boolean> = toSignal(
    this.breakpointObserver.observe(DESKTOP_QUERY).pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_QUERY) }
  );

  ngOnInit(): void {
    void this.store.loadShipments();
  }

  protected closeDetail(): void {
    void this.router.navigate(['/shipments']);
  }
}
