import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import {
  TruckDetailResponse,
  TruckListingRow,
  TruckRouteHistoryResponse,
  TruckSafetyEventEntry,
} from './trucks.models';

const BASE_URL = `${environment.apiUrl}/api/sw-expedited/trucks`;

@Service()
export class TrucksApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(): Observable<TruckListingRow[]> {
    return this.http.get<TruckListingRow[]>(BASE_URL);
  }

  detail(truckId: string): Observable<TruckDetailResponse> {
    return this.http.get<TruckDetailResponse>(`${BASE_URL}/${truckId}`);
  }

  // No startTime/endTime query params - the backend defaults to "today", which is all the route map ever needs.
  routeHistory(truckId: string): Observable<TruckRouteHistoryResponse> {
    return this.http.get<TruckRouteHistoryResponse>(`${BASE_URL}/${truckId}/route-history`);
  }

  safetyEvents(truckId: string): Observable<TruckSafetyEventEntry[]> {
    return this.http.get<TruckSafetyEventEntry[]>(`${BASE_URL}/${truckId}/safety-events`);
  }
}
