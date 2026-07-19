import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { ScheduleApi } from './schedule-api';
import { DAY_MS, DEFAULT_RANGE_DAYS, MAX_RANGE_DAYS, startOfDayMs } from './schedule-chart';
import { DriverScheduleRow, ManifestEta, ManifestRoute, ManifestSegment } from './schedule.models';

export type ScheduleRequestStatus = 'idle' | 'loading' | 'error';

type ScheduleState = {
  rows: DriverScheduleRow[];
  status: ScheduleRequestStatus;
  rangeStartMs: number;
  rangeDays: number;
  selectedDriverId: string | null;
  selectedManifest: ManifestSegment | null;
  // Fetched once here (rather than by the map/detail panes independently) so both panes render the same data from a
  // single request instead of racing two separate fetches of the same manifest's route.
  selectedManifestRoute: ManifestRoute | null;
  // Fetched alongside the route (same "snapshot at open-time" lifecycle, not polled) - null when the backend 404s,
  // which it does once every stop on the manifest is already checked out (see ManifestEtaService's javadoc).
  selectedManifestEta: ManifestEta | null;
};

// A factory, not a static object - rangeStartMs needs to reflect "today" at store creation time (each visit to
// /schedule), not whatever moment this module happened to be first imported.
function initialState(): ScheduleState {
  return {
    rows: [],
    status: 'idle',
    rangeStartMs: startOfDayMs(Date.now()),
    rangeDays: DEFAULT_RANGE_DAYS,
    selectedDriverId: null,
    selectedManifest: null,
    selectedManifestRoute: null,
    selectedManifestEta: null,
  };
}

// Cleared alongside a week change - a selected manifest may not exist in the newly-loaded week's rows, so keeping it
// selected would leave the map/detail panes pointing at stale/orphaned data.
const NO_SELECTION = {
  selectedDriverId: null,
  selectedManifest: null,
  selectedManifestRoute: null,
  selectedManifestEta: null,
} as const;

// Route-scoped (provided by SchedulePage, not `root`) so state resets per visit to /schedule, matching
// DriversStore/ShipmentsStore.
export const ScheduleStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const scheduleApi = inject(ScheduleApi);

    async function loadSchedule(): Promise<void> {
      patchState(store, { status: 'loading' });
      try {
        const rows = await firstValueFrom(scheduleApi.list(store.rangeStartMs(), store.rangeDays()));
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
          const rows = await firstValueFrom(scheduleApi.list(store.rangeStartMs(), store.rangeDays()));
          patchState(store, { rows });
        } catch {
          // Silent: a transient refresh failure shouldn't disrupt an already-rendered schedule.
        }
      },

      // Pages by the *currently selected* range length (not always a fixed week), so a dispatcher who's widened the
      // view to e.g. 14 days keeps paging in 14-day steps rather than snapping back to a week-sized jump.
      async goToPreviousRange(): Promise<void> {
        patchState(store, { rangeStartMs: store.rangeStartMs() - store.rangeDays() * DAY_MS, ...NO_SELECTION });
        await loadSchedule();
      },

      async goToNextRange(): Promise<void> {
        patchState(store, { rangeStartMs: store.rangeStartMs() + store.rangeDays() * DAY_MS, ...NO_SELECTION });
        await loadSchedule();
      },

      // Always resets to the default one-week view, discarding any custom range length - "Today" is a reset
      // affordance, not a "shift the current range to include today" one.
      async goToToday(): Promise<void> {
        patchState(store, {
          rangeStartMs: startOfDayMs(Date.now()),
          rangeDays: DEFAULT_RANGE_DAYS,
          ...NO_SELECTION,
        });
        await loadSchedule();
      },

      // Commits a custom range picked from the date-range-picker. Clamps rangeDays defensively - the picker's own
      // transformDates already enforces the 31-day max, but this is the one place every range change actually lands.
      async setRange(rangeStartMs: number, rangeDays: number): Promise<void> {
        patchState(store, {
          rangeStartMs,
          rangeDays: Math.min(rangeDays, MAX_RANGE_DAYS),
          ...NO_SELECTION,
        });
        await loadSchedule();
      },

      async selectManifest(driverId: string, manifest: ManifestSegment): Promise<void> {
        patchState(store, {
          selectedDriverId: driverId,
          selectedManifest: manifest,
          selectedManifestRoute: null,
          selectedManifestEta: null,
        });
        // Independent requests, fired together rather than sequentially - a slow/failing eta fetch shouldn't delay
        // the route (or vice versa), since the map and detail panes consume them separately.
        await Promise.all([
          (async (): Promise<void> => {
            try {
              const selectedManifestRoute = await firstValueFrom(scheduleApi.route(manifest.manifestNumber));
              // Guards against a stale response landing after the dispatcher has already selected a different manifest.
              if (store.selectedManifest()?.manifestNumber === manifest.manifestNumber) {
                patchState(store, { selectedManifestRoute });
              }
            } catch {
              // Silent - the map/detail panes handle a null route by simply not rendering stops yet.
            }
          })(),
          (async (): Promise<void> => {
            try {
              const selectedManifestEta = await firstValueFrom(scheduleApi.eta(manifest.manifestNumber));
              if (store.selectedManifest()?.manifestNumber === manifest.manifestNumber) {
                patchState(store, { selectedManifestEta });
              }
            } catch {
              // Silent (including a 404, which just means every stop is already checked out) - the detail pane
              // handles a null eta by simply not rendering an ETA block.
            }
          })(),
        ]);
      },

      clearSelection(): void {
        patchState(store, NO_SELECTION);
      },
    };
  })
);

export type ScheduleStoreType = InstanceType<typeof ScheduleStore>;
