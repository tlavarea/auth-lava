export type UserResponse = {
  id: number;
  email: string;
  emailVerified: boolean;
  mfaEnabled: boolean;
  authorities: string[];
};

export type StartRegistrationRequest = {
  email: string;
};

export type VerifyRegistrationCodeRequest = {
  email: string;
  code: string;
};

export type RegistrationTokenResponse = {
  registrationToken: string;
};

export type CompleteRegistrationRequest = {
  registrationToken: string;
  password: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type LogoutRequest = {
  allDevices: boolean;
};

export type TotpEnrollment = {
  mfaMethodId: number;
  secret: string;
  otpAuthUri: string;
  qrCodeDataUri: string;
};

export type MfaEnrollVerifyRequest = {
  mfaMethodId: number;
  code: string;
};

export type BackupCodesResponse = {
  backupCodes: string[];
};

export type MfaVerifyRequest = {
  code: string;
};

export type MfaDisableRequest = {
  code: string;
};

export type PasswordChangeRequest = {
  currentPassword: string;
  newPassword: string;
};

export type ApiErrorBody = {
  error: string;
};
