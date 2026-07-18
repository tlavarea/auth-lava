import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '@env/environment';
import { DriverScheduleRow } from './schedule.models';

const BASE_URL = `${environment.apiUrl}/api/sw-expedited/drivers/timeline`;

@Service()
export class ScheduleApi {
  private readonly http: HttpClient = inject(HttpClient);

  list(): Observable<DriverScheduleRow[]> {
    return this.http.get<DriverScheduleRow[]>(BASE_URL);
  }
}
