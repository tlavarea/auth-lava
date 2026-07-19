import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import { DAY_MS } from './schedule-chart';
import { DriverScheduleRow, ManifestDriverLocation, ManifestEta, ManifestRoute } from './schedule.models';

const TIMELINE_URL = `${environment.apiUrl}/api/sw-expedited/drivers/timeline`;
const MANIFESTS_URL = `${environment.apiUrl}/api/sw-expedited/manifests`;

@Service()
export class ScheduleApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(rangeStartMs: number, rangeDays: number): Observable<DriverScheduleRow[]> {
    return this.http.get<DriverScheduleRow[]>(TIMELINE_URL, {
      params: {
        weekStart: new Date(rangeStartMs).toISOString(),
        end: new Date(rangeStartMs + rangeDays * DAY_MS).toISOString(),
      },
    });
  }

  route(manifestNumber: number): Observable<ManifestRoute> {
    return this.http.get<ManifestRoute>(`${MANIFESTS_URL}/${manifestNumber}/route`);
  }

  driverLocation(manifestNumber: number): Observable<ManifestDriverLocation> {
    return this.http.get<ManifestDriverLocation>(`${MANIFESTS_URL}/${manifestNumber}/driver-location`);
  }

  eta(manifestNumber: number): Observable<ManifestEta> {
    return this.http.get<ManifestEta>(`${MANIFESTS_URL}/${manifestNumber}/eta`);
  }
}
