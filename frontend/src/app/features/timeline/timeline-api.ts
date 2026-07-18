import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import { DriverTimelineRow } from './timeline.models';

const BASE_URL = `${environment.apiUrl}/api/sw-expedited/drivers/timeline`;

@Service()
export class TimelineApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(): Observable<DriverTimelineRow[]> {
    return this.http.get<DriverTimelineRow[]>(BASE_URL);
  }
}
