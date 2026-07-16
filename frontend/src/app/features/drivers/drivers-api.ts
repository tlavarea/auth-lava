import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import { DriverDetailResponse, DriverListingRow } from './drivers.models';

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
}
