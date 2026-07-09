import { Component, inject, signal, WritableSignal } from '@angular/core';
import { Router } from '@angular/router';
import { BrnInputOtp } from '@spartan-ng/brain/input-otp';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmCheckboxImports } from '@spartan-ng/helm/checkbox';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputOtpImports } from '@spartan-ng/helm/input-otp';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { TotpEnrollment } from '@core/auth/auth.models';
import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { extractErrorMessage } from '@core/auth/extract-error-message';

type Step = 'loading' | 'verify' | 'backup-codes';

@Component({
  selector: 'app-mfa-enroll',
  imports: [
    BrnInputOtp,
    HlmAlertImports,
    HlmButtonImports,
    HlmCardImports,
    HlmCheckboxImports,
    HlmFieldImports,
    HlmInputOtpImports,
    HlmSpinnerImports,
  ],
  template: `
    <div class="flex min-h-dvh items-center justify-center p-4">
      <div hlmCard class="w-full max-w-sm">
        <div hlmCardHeader>
          <h1 hlmCardTitle>Set up two-factor authentication</h1>
          <p hlmCardDescription>Protect your account with an authenticator app.</p>
        </div>
        <div hlmCardContent class="flex flex-col gap-4">
          @if (errorMessage()) {
            <div hlmAlert variant="destructive">
              <p hlmAlertDescription>{{ errorMessage() }}</p>
            </div>
          }

          @switch (step()) {
            @case ('loading') {
              <div class="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
                <hlm-spinner />
                Preparing enrollment...
              </div>
            }
            @case ('verify') {
              @if (enrollment(); as enrollment) {
                <img
                  width="200"
                  height="200"
                  class="mx-auto"
                  alt="QR code for authenticator app enrollment"
                  [src]="enrollment.qrCodeDataUri" />
                <p class="text-center text-sm text-muted-foreground">
                  Scan this QR code with your authenticator app, then enter the 6-digit code it generates.
                </p>

                <div hlmField class="items-center">
                  <label hlmFieldLabel for="otp">Verification code</label>
                  <brn-input-otp
                    id="otp"
                    hlmInputOtp
                    [maxLength]="6"
                    [value]="code()"
                    (valueChange)="code.set($event)"
                    (completed)="onVerify()">
                    <hlm-input-otp-group>
                      <hlm-input-otp-slot [index]="0" />
                    </hlm-input-otp-group>
                    <hlm-input-otp-group>
                      <hlm-input-otp-slot [index]="1" />
                    </hlm-input-otp-group>
                    <hlm-input-otp-group>
                      <hlm-input-otp-slot [index]="2" />
                    </hlm-input-otp-group>
                    <hlm-input-otp-group>
                      <hlm-input-otp-slot [index]="3" />
                    </hlm-input-otp-group>
                    <hlm-input-otp-group>
                      <hlm-input-otp-slot [index]="4" />
                    </hlm-input-otp-group>
                    <hlm-input-otp-group>
                      <hlm-input-otp-slot [index]="5" />
                    </hlm-input-otp-group>
                  </brn-input-otp>
                </div>

                <button hlmBtn type="button" [disabled]="code().length !== 6 || verifying()" (click)="onVerify()">
                  @if (verifying()) {
                    <hlm-spinner />
                    Verifying...
                  } @else {
                    Verify
                  }
                </button>
              }
            }
            @case ('backup-codes') {
              <p class="text-sm text-muted-foreground">
                Save these backup codes somewhere safe. Each one can be used once to sign in if you lose access to your
                authenticator app. They won't be shown again.
              </p>
              <ul class="grid grid-cols-2 gap-2 rounded-md border p-4 font-mono text-sm">
                @for (backupCode of backupCodes(); track backupCode) {
                  <li>{{ backupCode }}</li>
                }
              </ul>

              <div hlmField orientation="horizontal">
                <hlm-checkbox
                  id="confirm-saved"
                  [checked]="confirmedSaved()"
                  (checkedChange)="confirmedSaved.set($event)" />
                <label hlmFieldLabel for="confirm-saved">I've saved these backup codes</label>
              </div>

              <button hlmBtn type="button" [disabled]="!confirmedSaved()" (click)="finish()">Continue</button>
            }
          }
        </div>
      </div>
    </div>
  `,
})
export class MfaEnrollPage {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly router: Router = inject(Router);

  protected readonly step: WritableSignal<Step> = signal<Step>('loading');
  protected readonly enrollment: WritableSignal<TotpEnrollment | null> = signal<TotpEnrollment | null>(null);
  protected readonly backupCodes: WritableSignal<string[]> = signal<string[]>([]);
  protected readonly code: WritableSignal<string> = signal('');
  protected readonly errorMessage: WritableSignal<string | null> = signal<string | null>(null);
  protected readonly verifying: WritableSignal<boolean> = signal(false);
  protected readonly confirmedSaved: WritableSignal<boolean> = signal(false);

  constructor() {
    void this.startEnrollment();
  }

  private async startEnrollment(): Promise<void> {
    try {
      const enrollment: TotpEnrollment = await this.authStore.enrollMfa();
      this.enrollment.set(enrollment);
      this.step.set('verify');
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error));
    }
  }

  protected async onVerify(): Promise<void> {
    const enrollment: TotpEnrollment | null = this.enrollment();

    if (!enrollment || this.code().length !== 6) {
      return;
    }

    this.errorMessage.set(null);
    this.verifying.set(true);

    try {
      const { backupCodes } = await this.authStore.verifyEnrollment(enrollment.mfaMethodId, this.code());
      this.backupCodes.set(backupCodes);
      this.step.set('backup-codes');
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error));
    } finally {
      this.verifying.set(false);
    }
  }

  protected finish(): void {
    void this.router.navigateByUrl('/');
  }
}
