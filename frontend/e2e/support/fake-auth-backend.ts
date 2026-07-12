import type { Page, Route } from '@playwright/test';

import type {
  ApiErrorBody,
  BackupCodesResponse,
  CompleteRegistrationRequest,
  EmailChangeStartRequest,
  EmailChangeVerifyRequest,
  LoginRequest,
  MfaDisableRequest,
  MfaEnrollVerifyRequest,
  MfaVerifyRequest,
  PasswordChangeRequest,
  StartRegistrationRequest,
  TotpEnrollment,
  UserResponse,
  VerifyRegistrationCodeRequest,
} from '../../src/app/core/auth/auth.models';

export const TEST_PASSWORD = 'CorrectHorseBattery9!';
export const REGISTRATION_CODE = '111222';
export const MFA_CODE = '654321';
export const EMAIL_CHANGE_CODE = '778899';

const DEFAULT_USER: UserResponse = {
  id: 1,
  email: 'jane@example.com',
  emailVerified: true,
  mfaEnabled: false,
  authorities: ['ROLE_USER'],
};

type Session = 'anonymous' | 'mfa-pending' | 'authenticated';

function errorBody(message: string): ApiErrorBody {
  return { error: message };
}

/**
 * Stands in for the Spring Boot backend's /api/auth/** endpoints. Mirrors the
 * contract in src/app/core/auth/auth-api.ts and auth.models.ts, not the Java
 * source — that's what the frontend actually talks to.
 */
export class FakeAuthBackend {
  private session: Session = 'anonymous';
  private user: UserResponse = { ...DEFAULT_USER };
  private password = TEST_PASSWORD;
  private registrationEmail = '';
  private readonly registrationCode = REGISTRATION_CODE;
  private readonly registrationToken = 'fake-registration-token';
  private readonly mfaMethodId = 42;
  private readonly mfaSecret = 'JBSWY3DPEHPK3PXP';
  private readonly mfaCode = MFA_CODE;
  private pendingEmail = '';
  private readonly emailChangeCode = EMAIL_CHANGE_CODE;
  private refreshSucceeds = false;

  withAnonymousUser(): this {
    this.session = 'anonymous';
    return this;
  }

  /** Configures the account that exists server-side, without changing the current session. */
  withRegisteredUser(overrides: Partial<UserResponse> = {}): this {
    this.user = { ...DEFAULT_USER, ...overrides };
    return this;
  }

  withAuthenticatedUser(overrides: Partial<UserResponse> = {}): this {
    this.user = { ...DEFAULT_USER, ...overrides };
    this.session = 'authenticated';
    return this;
  }

  withMfaPendingUser(overrides: Partial<UserResponse> = {}): this {
    this.user = { ...DEFAULT_USER, mfaEnabled: true, ...overrides };
    this.session = 'mfa-pending';
    return this;
  }

  withRefreshSucceeding(): this {
    this.refreshSucceeds = true;
    return this;
  }

  async install(page: Page): Promise<void> {
    await page.route('**/api/auth/**', (route: Route) => this.handle(route));
  }

  private async handle(route: Route): Promise<void> {
    const request = route.request();
    const method = request.method();
    const path = new URL(request.url()).pathname.replace(/^.*\/api\/auth/, '');
    const key = `${method} ${path}`;

    switch (key) {
      case 'POST /register/start': {
        const { email } = request.postDataJSON() as StartRegistrationRequest;
        this.registrationEmail = email;
        await route.fulfill({ status: 200, json: {} });
        return;
      }
      case 'POST /register/verify-code': {
        const { email, code } = request.postDataJSON() as VerifyRegistrationCodeRequest;
        if (email === this.registrationEmail && code === this.registrationCode) {
          await route.fulfill({ status: 200, json: { registrationToken: this.registrationToken } });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Invalid or expired verification code') });
        }
        return;
      }
      case 'POST /register/complete': {
        const { registrationToken } = request.postDataJSON() as CompleteRegistrationRequest;
        if (registrationToken === this.registrationToken) {
          await route.fulfill({ status: 200, json: {} });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Invalid or expired registration session') });
        }
        return;
      }
      case 'POST /login': {
        const { email, password } = request.postDataJSON() as LoginRequest;
        if (email === this.user.email && password === this.password) {
          this.session = this.user.mfaEnabled ? 'mfa-pending' : 'authenticated';
          await route.fulfill({ status: 200, json: this.user });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Invalid email or password') });
        }
        return;
      }
      case 'GET /me': {
        if (this.session === 'authenticated') {
          await route.fulfill({ status: 200, json: this.user });
        } else if (this.session === 'mfa-pending') {
          await route.fulfill({ status: 403, json: errorBody('MFA verification required.') });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Unauthorized.') });
        }
        return;
      }
      case 'POST /refresh': {
        if (this.refreshSucceeds) {
          await route.fulfill({ status: 200, json: {} });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Unauthorized.') });
        }
        return;
      }
      case 'POST /logout': {
        this.session = 'anonymous';
        await route.fulfill({ status: 200, json: {} });
        return;
      }
      case 'POST /mfa/enroll': {
        const enrollment: TotpEnrollment = {
          mfaMethodId: this.mfaMethodId,
          secret: this.mfaSecret,
          otpAuthUri: `otpauth://totp/auth-lava:${this.user.email}?secret=${this.mfaSecret}&issuer=auth-lava`,
          qrCodeDataUri:
            'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
        };
        await route.fulfill({ status: 200, json: enrollment });
        return;
      }
      case 'POST /mfa/enroll/verify': {
        const { mfaMethodId, code } = request.postDataJSON() as MfaEnrollVerifyRequest;
        if (mfaMethodId === this.mfaMethodId && code === this.mfaCode) {
          this.user = { ...this.user, mfaEnabled: true };
          const backupCodes: BackupCodesResponse = { backupCodes: ['aaaa-1111', 'bbbb-2222', 'cccc-3333'] };
          await route.fulfill({ status: 200, json: backupCodes });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Invalid verification code') });
        }
        return;
      }
      case 'POST /mfa/verify': {
        const { code } = request.postDataJSON() as MfaVerifyRequest;
        if (this.session === 'mfa-pending' && code === this.mfaCode) {
          this.session = 'authenticated';
          await route.fulfill({ status: 200, json: this.user });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Invalid verification code') });
        }
        return;
      }
      case 'DELETE /mfa': {
        const { code } = request.postDataJSON() as MfaDisableRequest;
        if (code === this.mfaCode) {
          this.user = { ...this.user, mfaEnabled: false };
          await route.fulfill({ status: 200, json: this.user });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Invalid verification code') });
        }
        return;
      }
      case 'PATCH /password': {
        const { currentPassword, newPassword } = request.postDataJSON() as PasswordChangeRequest;
        if (currentPassword === this.password) {
          this.password = newPassword;
          await route.fulfill({ status: 200, json: {} });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Current password is incorrect') });
        }
        return;
      }
      case 'POST /email/change': {
        const { newEmail } = request.postDataJSON() as EmailChangeStartRequest;
        this.pendingEmail = newEmail;
        await route.fulfill({ status: 200, json: {} });
        return;
      }
      case 'POST /email/change/verify': {
        const { code } = request.postDataJSON() as EmailChangeVerifyRequest;
        if (code === this.emailChangeCode) {
          this.user = { ...this.user, email: this.pendingEmail };
          await route.fulfill({ status: 200, json: this.user });
        } else {
          await route.fulfill({ status: 401, json: errorBody('Invalid or expired verification code') });
        }
        return;
      }
      default:
        await route.fulfill({ status: 404, json: errorBody(`Unhandled auth endpoint: ${key}`) });
    }
  }
}
