import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { patchState, signalStore, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';

import { AuthApi } from './auth-api';
import { BackupCodesResponse, LoginRequest, RegisterRequest, TotpEnrollment, UserResponse } from './auth.models';

export type AuthStatus = 'unknown' | 'authenticated' | 'mfa-pending' | 'anonymous';

type AuthState = {
  status: AuthStatus;
  user: UserResponse | null;
};

const initialState: AuthState = {
  status: 'unknown',
  user: null,
};

function isForbidden(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 403;
}

export const AuthStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withMethods((store) => {
    const authApi = inject(AuthApi);

    return {
      async bootstrap(): Promise<void> {
        try {
          const user = await firstValueFrom(authApi.me());
          patchState(store, { status: 'authenticated', user });
        } catch (error) {
          patchState(
            store,
            isForbidden(error) ? { status: 'mfa-pending', user: null } : { status: 'anonymous', user: null }
          );
        }
      },

      async register(payload: RegisterRequest): Promise<void> {
        await firstValueFrom(authApi.register(payload));
      },

      async login(payload: LoginRequest): Promise<void> {
        await firstValueFrom(authApi.login(payload));
        try {
          const user = await firstValueFrom(authApi.me());
          patchState(store, { status: 'authenticated', user });
        } catch (error) {
          if (!isForbidden(error)) {
            throw error;
          }
          patchState(store, { status: 'mfa-pending', user: null });
        }
      },

      async logout(): Promise<void> {
        try {
          await firstValueFrom(authApi.logout());
        } catch (error) {
          if (!isForbidden(error)) {
            throw error;
          }
        } finally {
          patchState(store, { status: 'anonymous', user: null });
        }
      },

      forceLogout(): void {
        patchState(store, { status: 'anonymous', user: null });
      },

      async enrollMfa(): Promise<TotpEnrollment> {
        return firstValueFrom(authApi.enrollMfa());
      },

      async verifyEnrollment(mfaMethodId: number, code: string): Promise<BackupCodesResponse> {
        return firstValueFrom(authApi.verifyEnrollment({ mfaMethodId, code }));
      },

      async verifyMfa(code: string): Promise<void> {
        const user = await firstValueFrom(authApi.verifyMfa({ code }));
        patchState(store, { status: 'authenticated', user });
      },
    };
  })
);
