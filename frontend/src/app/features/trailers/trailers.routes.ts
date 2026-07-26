import { Routes } from '@angular/router';

export const trailersRoutes: Routes = [
  {
    path: '',
    title: 'Trailers',
    loadComponent: () => import('./trailers.page').then((m) => m.TrailersPage),
    children: [
      {
        path: ':id',
        loadComponent: () => import('./trailer-detail/trailer-detail.page').then((m) => m.TrailerDetailPage),
      },
    ],
  },
];
