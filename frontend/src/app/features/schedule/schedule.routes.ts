import { Routes } from '@angular/router';

export const scheduleRoutes: Routes = [
  {
    path: '',
    title: 'Schedule',
    loadComponent: () => import('./schedule.page').then((m) => m.SchedulePage),
  },
];
