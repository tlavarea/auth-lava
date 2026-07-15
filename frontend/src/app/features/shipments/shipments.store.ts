import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { ShipmentsApi } from './shipments-api';
import { OfferResponseRequest, ShipmentDetailResponse, ShipmentListingRow } from './shipments.models';

export type ShipmentsRequestStatus = 'idle' | 'loading' | 'error';

type ShipmentsState = {
  shipments: ShipmentListingRow[];
  listStatus: ShipmentsRequestStatus;
  selectedDetail: ShipmentDetailResponse | null;
  detailStatus: ShipmentsRequestStatus;
  respondStatus: ShipmentsRequestStatus;
};

const initialState: ShipmentsState = {
  shipments: [],
  listStatus: 'idle',
  selectedDetail: null,
  detailStatus: 'idle',
  respondStatus: 'idle',
};

// Route-scoped (provided by ShipmentsPage, not `root`) so list + selected-detail state resets per visit
// to /shipments and is shared between the list and detail child route without a duplicate fetch.
export const ShipmentsStore = signalStore(
  withState(initialState),
  withMethods((store) => {
    const shipmentsApi = inject(ShipmentsApi);

    return {
      async loadShipments(): Promise<void> {
        patchState(store, { listStatus: 'loading' });
        try {
          const shipments = await firstValueFrom(shipmentsApi.list());
          patchState(store, { shipments, listStatus: 'idle' });
        } catch {
          patchState(store, { listStatus: 'error' });
        }
      },

      async loadShipmentDetail(offerId: number): Promise<void> {
        patchState(store, { detailStatus: 'loading', selectedDetail: null });
        try {
          const selectedDetail = await firstValueFrom(shipmentsApi.detail(offerId));
          patchState(store, { selectedDetail, detailStatus: 'idle' });
        } catch {
          patchState(store, { detailStatus: 'error' });
        }
      },

      clearSelectedDetail(): void {
        patchState(store, { selectedDetail: null, detailStatus: 'idle' });
      },

      async respondToOffer(offerId: number, request: OfferResponseRequest): Promise<void> {
        patchState(store, { respondStatus: 'loading' });
        try {
          await firstValueFrom(shipmentsApi.respondToOffer(offerId, request));
          patchState(store, { respondStatus: 'idle' });
        } catch (error) {
          patchState(store, { respondStatus: 'error' });
          throw error;
        }
      },
    };
  })
);

export type ShipmentsStoreType = InstanceType<typeof ShipmentsStore>;
