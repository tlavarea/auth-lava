export type UserResponse = {
  id: number;
  email: string;
  emailVerified: boolean;
  authorities: string[];
};

export type RegisterRequest = {
  email: string;
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

export type ApiErrorBody = {
  error: string;
};
