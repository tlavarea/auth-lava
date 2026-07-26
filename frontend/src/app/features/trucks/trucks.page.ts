import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, OnInit, Signal, signal, WritableSignal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { TruckDetailPage } from './truck-detail/truck-detail.page';
import { TruckItem } from './truck-item/truck-item';
import { TruckTable } from './truck-table/truck-table';
import { TruckListingRow } from './trucks.models';
import { TrucksRequestStatus, TrucksStore, TrucksStoreType } from './trucks.store';

// Matches Tailwind's `lg` breakpoint used for the JS-driven table/item mount decision below.
const DESKTOP_QUERY = '(min-width: 1024px)';

@Component({
  selector: 'app-trucks',
  imports: [HlmSpinnerImports, RouterOutlet, TruckItem, TruckTable],
  providers: [TrucksStore],
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
            <p class="text-destructive">Couldn't load trucks.</p>
          }
          @default {
            @if (isDesktop() && !detailOpen()) {
              <app-truck-table [trucks]="trucks()" />
            } @else {
              <app-truck-item [trucks]="trucks()" [selectedId]="selectedId()" />
            }
          }
        }
      </section>

      @if (detailOpen() && !isDesktop()) {
        <button
          type="button"
          class="fixed inset-0 z-40 bg-black/50"
          aria-label="Close truck details"
          (click)="closeDetail()"></button>
      }

      <div [class]="panelClasses()" [attr.inert]="!detailOpen() && !isDesktop() ? '' : null">
        <router-outlet (activate)="activeDetail.set($event)" (deactivate)="activeDetail.set(null)" />
      </div>
    </div>
  `,
})
export class TrucksPage implements OnInit {
  private readonly store: TrucksStoreType = inject(TrucksStore);
  private readonly breakpointObserver: BreakpointObserver = inject(BreakpointObserver);
  private readonly router: Router = inject(Router);

  protected readonly trucks: Signal<TruckListingRow[]> = this.store.trucks;
  protected readonly listStatus: Signal<TrucksRequestStatus> = this.store.listStatus;

  protected readonly activeDetail: WritableSignal<TruckDetailPage | null> = signal(null);
  protected readonly detailOpen: Signal<boolean> = computed(() => this.activeDetail() !== null);
  protected readonly selectedId: Signal<string | null> = computed(() => this.activeDetail()?.id() ?? null);

  protected readonly isDesktop: Signal<boolean> = toSignal(
    this.breakpointObserver.observe(DESKTOP_QUERY).pipe(map((state) => state.matches)),
    { initialValue: this.breakpointObserver.isMatched(DESKTOP_QUERY) }
  );

  // Same master/detail sizing convention as DriversPage - see its comment for the full rationale.
  protected readonly masterClasses: Signal<string> = computed(() => {
    if (!this.isDesktop()) {
      return 'h-full w-full overflow-y-auto p-4 [scrollbar-width:none]';
    }
    return this.detailOpen()
      ? 'h-full w-full max-w-md shrink-0 overflow-hidden bg-muted/20 p-4 [scrollbar-width:none]'
      : 'h-full w-full overflow-hidden p-4 [scrollbar-width:none]';
  });

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
    void this.store.loadTrucks();
  }

  protected closeDetail(): void {
    void this.router.navigate(['/trucks']);
  }
}
