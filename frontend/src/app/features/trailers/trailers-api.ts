import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import { TrailerDetailResponse, TrailerListingRow } from './trailers.models';

const BASE_URL = `${environment.apiUrl}/api/sw-expedited/trailers`;

@Service()
export class TrailersApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(): Observable<TrailerListingRow[]> {
    return this.http.get<TrailerListingRow[]>(BASE_URL);
  }

  detail(trailerId: string): Observable<TrailerDetailResponse> {
    return this.http.get<TrailerDetailResponse>(`${BASE_URL}/${trailerId}`);
  }
}
