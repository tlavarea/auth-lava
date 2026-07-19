import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { ScheduleApi } from './schedule-api';
import { startOfDayMs, WEEK_MS } from './schedule-chart';
import { DriverScheduleRow, ManifestRoute, ManifestSegment } from './schedule.models';

export type ScheduleRequestStatus = 'idle' | 'loading' | 'error';

type ScheduleState = {
  rows: DriverScheduleRow[];
  status: ScheduleRequestStatus;
  weekStartMs: number;
  selectedDriverId: string | null;
  selectedManifest: ManifestSegment | null;
  // Fetched once here (rather than by the map/detail panes independently) so both panes render the same data from a
  // single request instead of racing two separate fetches of the same manifest's route.
  selectedManifestRoute: ManifestRoute | null;
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
    selectedManifestRoute: null,
  };
}

// Cleared alongside a week change - a selected manifest may not exist in the newly-loaded week's rows, so keeping it
// selected would leave the map/detail panes pointing at stale/orphaned data.
const NO_SELECTION = { selectedDriverId: null, selectedManifest: null, selectedManifestRoute: null } as const;

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

      async selectManifest(driverId: string, manifest: ManifestSegment): Promise<void> {
        patchState(store, { selectedDriverId: driverId, selectedManifest: manifest, selectedManifestRoute: null });
        try {
          const selectedManifestRoute = await firstValueFrom(scheduleApi.route(manifest.manifestNumber));
          // Guards against a stale response landing after the dispatcher has already selected a different manifest.
          if (store.selectedManifest()?.manifestNumber === manifest.manifestNumber) {
            patchState(store, { selectedManifestRoute });
          }
        } catch {
          // Silent - the map/detail panes handle a null route by simply not rendering stops yet.
        }
      },

      clearSelection(): void {
        patchState(store, NO_SELECTION);
      },
    };
  })
);

export type ScheduleStoreType = InstanceType<typeof ScheduleStore>;
