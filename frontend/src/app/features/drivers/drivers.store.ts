import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { DriversApi } from './drivers-api';
import { DriverDetailResponse, DriverListingRow } from './drivers.models';

export type DriversRequestStatus = 'idle' | 'loading' | 'error';

type DriversState = {
  drivers: DriverListingRow[];
  listStatus: DriversRequestStatus;
  selectedDetail: DriverDetailResponse | null;
  detailStatus: DriversRequestStatus;
};

const initialState: DriversState = {
  drivers: [],
  listStatus: 'idle',
  selectedDetail: null,
  detailStatus: 'idle',
};

// Route-scoped (provided by DriversPage, not `root`) so list + selected-detail state resets per visit
// to /drivers and is shared between the list and detail child route without a duplicate fetch.
export const DriversStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const driversApi = inject(DriversApi);

    return {
      async loadDrivers(): Promise<void> {
        patchState(store, { listStatus: 'loading' });
        try {
          const drivers = await firstValueFrom(driversApi.list());
          patchState(store, { drivers, listStatus: 'idle' });
        } catch {
          patchState(store, { listStatus: 'error' });
        }
      },

      async loadDriverDetail(driverId: string): Promise<void> {
        patchState(store, { detailStatus: 'loading', selectedDetail: null });
        try {
          const selectedDetail = await firstValueFrom(driversApi.detail(driverId));
          patchState(store, { selectedDetail, detailStatus: 'idle' });
        } catch {
          patchState(store, { detailStatus: 'error' });
        }
      },

      // Re-fetches the currently-selected driver's detail without resetting detailStatus/selectedDetail to
      // loading/null first - used to silently refresh the location while the detail pane stays open, since the
      // backend re-syncs vehicle locations roughly every minute (see SamsaraLocationSyncScheduler).
      async refreshDriverDetail(driverId: string): Promise<void> {
        try {
          const selectedDetail = await firstValueFrom(driversApi.detail(driverId));
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

export type DriversStoreType = InstanceType<typeof DriversStore>;
