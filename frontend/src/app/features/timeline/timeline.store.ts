import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { TimelineApi } from './timeline-api';
import { DriverTimelineRow } from './timeline.models';

export type TimelineRequestStatus = 'idle' | 'loading' | 'error';

type TimelineState = {
  rows: DriverTimelineRow[];
  status: TimelineRequestStatus;
};

const initialState: TimelineState = {
  rows: [],
  status: 'idle',
};

// Route-scoped (provided by TimelinePage, not `root`) so state resets per visit to /timeline, matching
// DriversStore/ShipmentsStore.
export const TimelineStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const timelineApi = inject(TimelineApi);

    return {
      async loadTimeline(): Promise<void> {
        patchState(store, { status: 'loading' });
        try {
          const rows = await firstValueFrom(timelineApi.list());
          patchState(store, { rows, status: 'idle' });
        } catch {
          patchState(store, { status: 'error' });
        }
      },

      // Re-fetches without resetting status/rows to loading/empty first, so the silent poll doesn't flash a
      // spinner over an already-rendered timeline - mirrors DriversStore.refreshDriverDetail.
      async refreshTimeline(): Promise<void> {
        try {
          const rows = await firstValueFrom(timelineApi.list());
          patchState(store, { rows });
        } catch {
          // Silent: a transient refresh failure shouldn't disrupt an already-rendered timeline.
        }
      },
    };
  })
);

export type TimelineStoreType = InstanceType<typeof TimelineStore>;
