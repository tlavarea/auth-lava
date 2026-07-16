import { Routes } from '@angular/router';

export const driversRoutes: Routes = [
  {
    path: '',
    title: 'Drivers',
    loadComponent: () => import('./drivers.page').then((m) => m.DriversPage),
    children: [
      {
        path: ':id',
        loadComponent: () => import('./driver-detail/driver-detail.page').then((m) => m.DriverDetailPage),
      },
    ],
  },
];
