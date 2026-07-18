import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { ScheduleApi } from './schedule-api';
import { startOfDayMs, WEEK_MS } from './schedule-chart';
import { DriverScheduleRow, ManifestSegment } from './schedule.models';

export type ScheduleRequestStatus = 'idle' | 'loading' | 'error';

type ScheduleState = {
  rows: DriverScheduleRow[];
  status: ScheduleRequestStatus;
  weekStartMs: number;
  selectedDriverId: string | null;
  selectedManifest: ManifestSegment | null;
};

// A factory, not a static object - weekStartMs needs to reflect "today" at store creation time (each visit to
// /schedule), not whatever moment this module happened to be first imported.
function initialState(): ScheduleState {
  return {
    rows: [],
    status: 'idle',
    weekStartMs: startOfDayMs(Date.now()),
    selectedDriverId: null,
    selectedManifest: null,
  };
}

// Cleared alongside a week change - a selected manifest may not exist in the newly-loaded week's rows, so keeping it
// selected would leave the map panel pointing at stale/orphaned data.
const NO_SELECTION = { selectedDriverId: null, selectedManifest: null } as const;

// Route-scoped (provided by SchedulePage, not `root`) so state resets per visit to /schedule, matching
// DriversStore/ShipmentsStore.
export const ScheduleStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const scheduleApi = inject(ScheduleApi);

    async function loadSchedule(): Promise<void> {
      patchState(store, { status: 'loading' });
      try {
        const rows = await firstValueFrom(scheduleApi.list(store.weekStartMs()));
        patchState(store, { rows, status: 'idle' });
      } catch {
        patchState(store, { status: 'error' });
      }
    }

    return {
      loadSchedule,

      // Re-fetches without resetting status/rows to loading/empty first, so the silent poll doesn't flash a
      // spinner over an already-rendered schedule - mirrors DriversStore.refreshDriverDetail.
      async refreshSchedule(): Promise<void> {
        try {
          const rows = await firstValueFrom(scheduleApi.list(store.weekStartMs()));
          patchState(store, { rows });
        } catch {
          // Silent: a transient refresh failure shouldn't disrupt an already-rendered schedule.
        }
      },

      async goToPreviousWeek(): Promise<void> {
        patchState(store, { weekStartMs: store.weekStartMs() - WEEK_MS, ...NO_SELECTION });
        await loadSchedule();
      },

      async goToNextWeek(): Promise<void> {
        patchState(store, { weekStartMs: store.weekStartMs() + WEEK_MS, ...NO_SELECTION });
        await loadSchedule();
      },

      async goToCurrentWeek(): Promise<void> {
        patchState(store, { weekStartMs: startOfDayMs(Date.now()), ...NO_SELECTION });
        await loadSchedule();
      },

      selectManifest(driverId: string, manifest: ManifestSegment): void {
        patchState(store, { selectedDriverId: driverId, selectedManifest: manifest });
      },

      clearSelection(): void {
        patchState(store, NO_SELECTION);
      },
    };
  })
);

export type ScheduleStoreType = InstanceType<typeof ScheduleStore>;
