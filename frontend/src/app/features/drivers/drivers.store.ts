import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { startOfTodayIso } from './date-utils';
import { DriversApi } from './drivers-api';
import { DriverActivityEntry, DriverDetailResponse, DriverListingRow } from './drivers.models';

export type DriversRequestStatus = 'idle' | 'loading' | 'error';

type DriversState = {
  drivers: DriverListingRow[];
  listStatus: DriversRequestStatus;
  selectedDetail: DriverDetailResponse | null;
  detailStatus: DriversRequestStatus;
  activity: DriverActivityEntry[];
  activityStatus: DriversRequestStatus;
};

const initialState: DriversState = {
  drivers: [],
  listStatus: 'idle',
  selectedDetail: null,
  detailStatus: 'idle',
  activity: [],
  activityStatus: 'idle',
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

      // Patches only the position fields of the currently-selected driver's detail from a live, on-demand
      // single-vehicle Samsara call (see DriversApi.liveLocation/backend's SamsaraDriverLiveLocationService) - polled
      // faster than refreshDriverDetail's ~60s cadence so the map's arrow visibly moves. A no-op if selectedDetail
      // has since been cleared (e.g. the detail view was closed mid-request) or driverId no longer matches it.
      async pollLiveLocation(driverId: string): Promise<void> {
        try {
          const liveLocation = await firstValueFrom(driversApi.liveLocation(driverId));
          const current = store.selectedDetail();
          if (current === null || current.id !== driverId) {
            return;
          }
          patchState(store, { selectedDetail: { ...current, ...liveLocation } });
        } catch {
          // Silent: a transient poll failure shouldn't disrupt an already-open detail view - the next poll retries.
        }
      },

      // Scoped to today (the viewer's local calendar day) rather than DriversApi.activity's own rolling-24h
      // server-side default, matching the panel's "Today" framing.
      async loadDriverActivity(driverId: string): Promise<void> {
        patchState(store, { activityStatus: 'loading', activity: [] });
        try {
          const activity = await firstValueFrom(driversApi.activity(driverId, startOfTodayIso()));
          patchState(store, { activity, activityStatus: 'idle' });
        } catch {
          patchState(store, { activityStatus: 'error' });
        }
      },

      // Re-fetches the currently-selected driver's activity feed without resetting activityStatus/activity to
      // loading/empty first, mirroring refreshDriverDetail - polled on the same ~60s cadence, since duty-status
      // changes aren't a sub-minute event.
      async refreshDriverActivity(driverId: string): Promise<void> {
        try {
          const activity = await firstValueFrom(driversApi.activity(driverId, startOfTodayIso()));
          patchState(store, { activity });
        } catch {
          // Silent: a transient refresh failure shouldn't disrupt an already-open detail view.
        }
      },

      clearSelectedDetail(): void {
        patchState(store, { selectedDetail: null, detailStatus: 'idle', activity: [], activityStatus: 'idle' });
      },
    };
  })
);

export type DriversStoreType = InstanceType<typeof DriversStore>;
