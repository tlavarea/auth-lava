import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, OnInit, Signal, signal, WritableSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { DriverDetailPage } from './driver-detail/driver-detail.page';
import { DriverItem } from './driver-item/driver-item';
import { DriverTable } from './driver-table/driver-table';
import { DriverListingRow } from './drivers.models';
import { DriversRequestStatus, DriversStore, DriversStoreType } from './drivers.store';

// Matches Tailwind's `lg` breakpoint used for the JS-driven table/item mount decision below.
const DESKTOP_QUERY = '(min-width: 1024px)';

@Component({
  selector: 'app-drivers',
  imports: [HlmSpinnerImports, RouterOutlet, DriverItem, DriverTable],
  providers: [DriversStore],
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
            <p class="text-destructive">Couldn't load drivers.</p>
          }
          @default {
            @if (isDesktop() && !detailOpen()) {
              <app-driver-table [drivers]="drivers()" />
            } @else {
              <app-driver-item [drivers]="drivers()" [selectedId]="selectedId()" />
            }
          }
        }
      </section>

      @if (detailOpen() && !isDesktop()) {
        <button
          type="button"
          class="fixed inset-0 z-40 bg-black/50"
          aria-label="Close driver details"
          (click)="closeDetail()"></button>
      }

      <div [class]="panelClasses()" [attr.inert]="!detailOpen() && !isDesktop() ? '' : null">
        <router-outlet (activate)="activeDetail.set($event)" (deactivate)="activeDetail.set(null)" />
      </div>
    </div>
  `,
})
export class DriversPage implements OnInit {
  private readonly store: DriversStoreType = inject(DriversStore);
  private readonly breakpointObserver: BreakpointObserver = inject(BreakpointObserver);
  private readonly router: Router = inject(Router);

  protected readonly drivers: Signal<DriverListingRow[]> = this.store.drivers;
  protected readonly listStatus: Signal<DriversRequestStatus> = this.store.listStatus;

  protected readonly activeDetail: WritableSignal<DriverDetailPage | null> = signal(null);
  protected readonly detailOpen: Signal<boolean> = computed(() => this.activeDetail() !== null);
  protected readonly selectedId: Signal<string | null> = computed(() => this.activeDetail()?.id() ?? null);

  protected readonly isDesktop: Signal<boolean> = toSignal(
    this.breakpointObserver.observe(DESKTOP_QUERY).pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_QUERY) }
  );

  // On desktop, an open detail shrinks the master column to an item-view sidebar; closed, it's full width.
  // Scrolling is frozen while a detail is open so the selected row can never be scrolled out of view.
  // Desktop-with-no-detail renders DriverTable, which manages its own internal scroll region (toolbar
  // and pagination stay fixed, only the card list scrolls) so the section itself must not scroll there.
  // Mobile keeps document-style scrolling since DriverItem has no sticky toolbar of its own.
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
    void this.store.loadDrivers();
  }

  protected closeDetail(): void {
    void this.router.navigate(['/drivers']);
  }
}
