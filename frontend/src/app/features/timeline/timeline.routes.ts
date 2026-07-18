import { Routes } from '@angular/router';

export const timelineRoutes: Routes = [
  {
    path: '',
    title: 'Timeline',
    loadComponent: () => import('./timeline.page').then((m) => m.TimelinePage),
  },
];
