import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { TrucksApi } from './trucks-api';
import { TruckDetailResponse, TruckListingRow } from './trucks.models';

export type TrucksRequestStatus = 'idle' | 'loading' | 'error';

type TrucksState = {
  trucks: TruckListingRow[];
  listStatus: TrucksRequestStatus;
  selectedDetail: TruckDetailResponse | null;
  detailStatus: TrucksRequestStatus;
};

const initialState: TrucksState = {
  trucks: [],
  listStatus: 'idle',
  selectedDetail: null,
  detailStatus: 'idle',
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
    };
  })
);

export type TrucksStoreType = InstanceType<typeof TrucksStore>;
