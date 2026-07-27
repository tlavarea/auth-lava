import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { TrucksApi } from './trucks-api';
import {
  TruckDetailResponse,
  TruckListingRow,
  TruckRouteHistoryResponse,
  TruckSafetyEventEntry,
} from './trucks.models';

export type TrucksRequestStatus = 'idle' | 'loading' | 'error';

type TrucksState = {
  trucks: TruckListingRow[];
  listStatus: TrucksRequestStatus;
  selectedDetail: TruckDetailResponse | null;
  detailStatus: TrucksRequestStatus;
  routeHistory: TruckRouteHistoryResponse | null;
  routeHistoryStatus: TrucksRequestStatus;
  safetyEvents: TruckSafetyEventEntry[] | null;
  safetyEventsStatus: TrucksRequestStatus;
};

const initialState: TrucksState = {
  trucks: [],
  listStatus: 'idle',
  selectedDetail: null,
  detailStatus: 'idle',
  routeHistory: null,
  routeHistoryStatus: 'idle',
  safetyEvents: null,
  safetyEventsStatus: 'idle',
};

// Route-scoped (provided by TrucksPage, not `root`) so list + selected-detail state resets per visit to
// /trucks and is shared between the list and detail child route without a duplicate fetch.
export const TrucksStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const trucksApi = inject(TrucksApi);

    return {
      async loadTrucks(): Promise<void> {
        patchState(store, { listStatus: 'loading' });
        try {
          const trucks = await firstValueFrom(trucksApi.list());
          patchState(store, { trucks, listStatus: 'idle' });
        } catch {
          patchState(store, { listStatus: 'error' });
        }
      },

      async loadTruckDetail(truckId: string): Promise<void> {
        patchState(store, { detailStatus: 'loading', selectedDetail: null });
        try {
          const selectedDetail = await firstValueFrom(trucksApi.detail(truckId));
          patchState(store, { selectedDetail, detailStatus: 'idle' });
        } catch {
          patchState(store, { detailStatus: 'error' });
        }
      },

      // Re-fetches the currently-selected truck's detail without resetting detailStatus/selectedDetail to
      // loading/null first - used to silently refresh diagnostics/location while the detail pane stays open, since
      // the backend re-syncs them roughly every 2 minutes (see SamsaraVehicleDiagnosticsSyncScheduler).
      async refreshTruckDetail(truckId: string): Promise<void> {
        try {
          const selectedDetail = await firstValueFrom(trucksApi.detail(truckId));
          patchState(store, { selectedDetail });
        } catch {
          // Silent: a transient refresh failure shouldn't disrupt an already-open detail view.
        }
      },

      clearSelectedDetail(): void {
        patchState(store, { selectedDetail: null, detailStatus: 'idle' });
      },

      // Fetched once per truck detail visit (not on the 60s diagnostics-refresh timer, unlike refreshTruckDetail) -
      // a full day of GPS history/safety events doesn't meaningfully change every 60s. The two requests are
      // independent (one failing shouldn't block the other), so each gets its own status/try-catch rather than
      // failing both on a single rejected Promise.all.
      async loadTruckMapData(truckId: string): Promise<void> {
        patchState(store, { routeHistoryStatus: 'loading', safetyEventsStatus: 'loading' });
        await Promise.all([
          (async (): Promise<void> => {
            try {
              const routeHistory = await firstValueFrom(trucksApi.routeHistory(truckId));
              patchState(store, { routeHistory, routeHistoryStatus: 'idle' });
            } catch {
              patchState(store, { routeHistoryStatus: 'error' });
            }
          })(),
          (async (): Promise<void> => {
            try {
              const safetyEvents = await firstValueFrom(trucksApi.safetyEvents(truckId));
              patchState(store, { safetyEvents, safetyEventsStatus: 'idle' });
            } catch {
              patchState(store, { safetyEventsStatus: 'error' });
            }
          })(),
        ]);
      },

      clearMapData(): void {
        patchState(store, {
          routeHistory: null,
          routeHistoryStatus: 'idle',
          safetyEvents: null,
          safetyEventsStatus: 'idle',
        });
      },
    };
  })
);

export type TrucksStoreType = InstanceType<typeof TrucksStore>;
