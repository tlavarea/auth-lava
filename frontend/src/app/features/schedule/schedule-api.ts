import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import { DriverScheduleRow, ManifestRoute } from './schedule.models';

const TIMELINE_URL = `${environment.apiUrl}/api/sw-expedited/drivers/timeline`;
const MANIFESTS_URL = `${environment.apiUrl}/api/sw-expedited/manifests`;

@Service()
export class ScheduleApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(weekStartMs: number): Observable<DriverScheduleRow[]> {
    return this.http.get<DriverScheduleRow[]>(TIMELINE_URL, {
      params: { weekStart: new Date(weekStartMs).toISOString() },
    });
  }

  route(manifestNumber: number): Observable<ManifestRoute> {
    return this.http.get<ManifestRoute>(`${MANIFESTS_URL}/${manifestNumber}/route`);
  }
}
