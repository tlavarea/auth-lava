import { Routes } from '@angular/router';

export const trucksRoutes: Routes = [
  {
    path: '',
    title: 'Trucks',
    loadComponent: () => import('./trucks.page').then((m) => m.TrucksPage),
    children: [
      {
        path: ':id',
        loadComponent: () => import('./truck-detail/truck-detail.page').then((m) => m.TruckDetailPage),
      },
    ],
  },
];
