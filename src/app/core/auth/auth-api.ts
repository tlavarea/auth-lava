import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  BackupCodesResponse,
  LoginRequest,
  LogoutRequest,
  MfaEnrollVerifyRequest,
  MfaVerifyRequest,
  RegisterRequest,
  TotpEnrollment,
  UserResponse,
} from './auth.models';

const BASE_URL = `${environment.apiUrl}/api/auth`;

@Service()
export class AuthApi {
  private readonly http = inject(HttpClient);

  register(payload: RegisterRequest): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/register`, payload);
  }

  login(payload: LoginRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${BASE_URL}/login`, payload);
  }

  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${BASE_URL}/me`);
  }

  refresh(): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/refresh`, {});
  }

  logout(payload: LogoutRequest = { allDevices: false }): Observable<void> {
    return this.http.post<void>(`${BASE_URL}/logout`, payload);
  }

  enrollMfa(): Observable<TotpEnrollment> {
    return this.http.post<TotpEnrollment>(`${BASE_URL}/mfa/enroll`, {});
  }

  verifyEnrollment(payload: MfaEnrollVerifyRequest): Observable<BackupCodesResponse> {
    return this.http.post<BackupCodesResponse>(`${BASE_URL}/mfa/enroll/verify`, payload);
  }

  verifyMfa(payload: MfaVerifyRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${BASE_URL}/mfa/verify`, payload);
  }
}
