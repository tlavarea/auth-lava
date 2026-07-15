import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import { ShipmentDetailResponse, ShipmentListingRow } from './shipments.models';

const BASE_URL = `${environment.apiUrl}/api/sw-expedited/shipments`;

@Service()
export class ShipmentsApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(): Observable<ShipmentListingRow[]> {
    return this.http.get<ShipmentListingRow[]>(BASE_URL);
  }

  detail(offerId: number): Observable<ShipmentDetailResponse> {
    return this.http.get<ShipmentDetailResponse>(`${BASE_URL}/${offerId}`);
  }
}
