import { Routes } from '@angular/router';

export const shipmentsRoutes: Routes = [
  {
    path: '',
    title: 'Shipments',
    loadComponent: () => import('./shipments.page').then((m) => m.ShipmentsPage),
    children: [
      {
        path: ':id',
        loadComponent: () => import('./shipment-detail/shipment-detail.page').then((m) => m.ShipmentDetailPage),
      },
    ],
  },
];
