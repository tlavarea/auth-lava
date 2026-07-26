import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { TrailersApi } from './trailers-api';
import { TrailerDetailResponse, TrailerListingRow } from './trailers.models';

export type TrailersRequestStatus = 'idle' | 'loading' | 'error';

type TrailersState = {
  trailers: TrailerListingRow[];
  listStatus: TrailersRequestStatus;
  selectedDetail: TrailerDetailResponse | null;
  detailStatus: TrailersRequestStatus;
};

const initialState: TrailersState = {
  trailers: [],
  listStatus: 'idle',
  selectedDetail: null,
  detailStatus: 'idle',
};

// Route-scoped (provided by TrailersPage, not `root`) so list + selected-detail state resets per visit to
// /trailers and is shared between the list and detail child route without a duplicate fetch.
export const TrailersStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const trailersApi = inject(TrailersApi);

    return {
      async loadTrailers(): Promise<void> {
        patchState(store, { listStatus: 'loading' });
        try {
          const trailers = await firstValueFrom(trailersApi.list());
          patchState(store, { trailers, listStatus: 'idle' });
        } catch {
          patchState(store, { listStatus: 'error' });
        }
      },

      async loadTrailerDetail(trailerId: string): Promise<void> {
        patchState(store, { detailStatus: 'loading', selectedDetail: null });
        try {
          const selectedDetail = await firstValueFrom(trailersApi.detail(trailerId));
          patchState(store, { selectedDetail, detailStatus: 'idle' });
        } catch {
          patchState(store, { detailStatus: 'error' });
        }
      },

      clearSelectedDetail(): void {
        patchState(store, { selectedDetail: null, detailStatus: 'idle' });
      },
    };
  })
);

export type TrailersStoreType = InstanceType<typeof TrailersStore>;
