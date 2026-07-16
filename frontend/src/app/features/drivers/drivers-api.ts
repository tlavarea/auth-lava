import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import {
  DriverActivityEntry,
  DriverDetailResponse,
  DriverListingRow,
  DriverLiveLocationResponse,
} from './drivers.models';

const BASE_URL = `${environment.apiUrl}/api/sw-expedited/drivers`;

@Service()
export class DriversApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(): Observable<DriverListingRow[]> {
    return this.http.get<DriverListingRow[]>(BASE_URL);
  }

  detail(driverId: string): Observable<DriverDetailResponse> {
    return this.http.get<DriverDetailResponse>(`${BASE_URL}/${driverId}`);
  }

  liveLocation(driverId: string): Observable<DriverLiveLocationResponse> {
    return this.http.get<DriverLiveLocationResponse>(`${BASE_URL}/${driverId}/location`);
  }

  // `since` defaults server-side to the last 24 hours when omitted (see backend's DriverController.activity).
  activity(driverId: string, since?: string): Observable<DriverActivityEntry[]> {
    return this.http.get<DriverActivityEntry[]>(`${BASE_URL}/${driverId}/activity`, {
      params: since ? { since } : {},
    });
  }
}
