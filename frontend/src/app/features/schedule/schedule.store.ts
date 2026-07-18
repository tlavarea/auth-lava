import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { ScheduleApi } from './schedule-api';
import { DriverScheduleRow } from './schedule.models';

export type ScheduleRequestStatus = 'idle' | 'loading' | 'error';

type ScheduleState = {
  rows: DriverScheduleRow[];
  status: ScheduleRequestStatus;
};

const initialState: ScheduleState = {
  rows: [],
  status: 'idle',
};

// Route-scoped (provided by SchedulePage, not `root`) so state resets per visit to /schedule, matching
// DriversStore/ShipmentsStore.
export const ScheduleStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const scheduleApi = inject(ScheduleApi);

    return {
      async loadSchedule(): Promise<void> {
        patchState(store, { status: 'loading' });
        try {
          const rows = await firstValueFrom(scheduleApi.list());
          patchState(store, { rows, status: 'idle' });
        } catch {
          patchState(store, { status: 'error' });
        }
      },

      // Re-fetches without resetting status/rows to loading/empty first, so the silent poll doesn't flash a
      // spinner over an already-rendered schedule - mirrors DriversStore.refreshDriverDetail.
      async refreshSchedule(): Promise<void> {
        try {
          const rows = await firstValueFrom(scheduleApi.list());
          patchState(store, { rows });
        } catch {
          // Silent: a transient refresh failure shouldn't disrupt an already-rendered schedule.
        }
      },
    };
  })
);

export type ScheduleStoreType = InstanceType<typeof ScheduleStore>;
