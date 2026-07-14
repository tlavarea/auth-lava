import { Component, inject, signal, WritableSignal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCopy, lucideCopyCheck } from '@ng-icons/lucide';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmDialogImports } from '@spartan-ng/helm/dialog';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { TotpEnrollment } from '@core/auth/auth.models';
import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { extractErrorMessage } from '@core/auth/extract-error-message';
import { MfaEnrollStep } from '@models/models';
import { OtpInput } from '@shared/otp-input/otp-input';

@Component({
  imports: [HlmAlertImports, HlmButtonImports, HlmDialogImports, HlmFieldImports, HlmSpinnerImports, OtpInput, NgIcon],
  viewProviders: [provideIcons({ lucideCopy, lucideCopyCheck })],
  template: `
    @if (errorMessage()) {
      <div hlmAlert variant="destructive">
        <p class="text-center" hlmAlertDescription>{{ errorMessage() }}</p>
      </div>
    }

    @switch (step()) {
      @case ('loading') {
        <hlm-dialog-header class="mb-4">
          <h3 hlmDialogTitle>Set up two-factor authentication</h3>
        </hlm-dialog-header>
        @if (!errorMessage()) {
          <div class="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
            <hlm-spinner />
            Preparing enrollment...
          </div>
        }
      }

      @case ('verify') {
        <hlm-dialog-header class="mb-4">
          <h3 hlmDialogTitle>Set up two-factor authentication</h3>
          <p hlmDialogDescription>Protect your account with an authenticator app.</p>
        </hlm-dialog-header>
        @if (enrollment(); as enrollment) {
          <img
            width="200"
            height="200"
            class="mx-auto"
            alt="QR code for authenticator app enrollment"
            [src]="enrollment.qrCodeDataUri" />
          <p class="text-sm text-muted-foreground">
            Scan this QR code with your authenticator app, then enter the 6-digit code it generates.
          </p>

          <div hlmField class="mt-6">
            <div class="flex items-center">
              <label hlmFieldLabel for="otp">Verification code</label>
              @if (verifyingCode()) {
                <hlm-spinner />
              }
            </div>
            <app-otp-input
              inputId="otp"
              [disabled]="verifyingCode()"
              [maxLength]="6"
              (valueChange)="code.set($event)"
              (completed)="onVerify()" />
          </div>
        }
      }

      @case ('backup-codes') {
        <hlm-dialog-header class="mb-4">
          <h3 hlmDialogTitle>Save your backup codes</h3>
        </hlm-dialog-header>
        <p class="text-sm text-muted-foreground">
          Save these backup codes somewhere safe. Each one can be used once to sign in if you lose access to your
          authenticator app. They won't be shown again.
        </p>
        <ul class="my-6 grid grid-cols-2 gap-2 rounded-md border p-4 font-mono text-sm">
          @for (backupCode of backupCodes(); track backupCode) {
            <li class="text-center">{{ backupCode }}</li>
          }
        </ul>

        <div class="flex gap-2">
          <button hlmBtn type="button" [disabled]="!confirmedSaved()" (click)="finish()">Continue</button>
          <button hlmBtn type="button" variant="outline" [disabled]="copySuccess()" (click)="copyRecoveryCodes()">
            <ng-icon [name]="copySuccess() ? 'lucideCopyCheck' : 'lucideCopy'" />
            {{ copySuccess() ? 'Copied' : 'Copy all' }}
          </button>
        </div>
      }
    }
  `,
})
export class MfaEnrollDialog {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly dialogRef: BrnDialogRef = inject(BrnDialogRef);

  protected readonly step: WritableSignal<MfaEnrollStep> = signal<MfaEnrollStep>('loading');
  protected readonly enrollment: WritableSignal<TotpEnrollment | null> = signal<TotpEnrollment | null>(null);
  protected readonly backupCodes: WritableSignal<string[]> = signal<string[]>([]);
  protected readonly code: WritableSignal<string> = signal('');
  protected readonly copySuccess: WritableSignal<boolean> = signal<boolean>(false);
  protected readonly errorMessage: WritableSignal<string | null> = signal<string | null>(null);
  protected readonly verifyingCode: WritableSignal<boolean> = signal(false);
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

  protected async copyRecoveryCodes(): Promise<void> {
    await navigator.clipboard.writeText(this.backupCodes().join('\n'));
    this.confirmedSaved.set(true);
    this.copySuccess.set(true);
    setTimeout((): void => {
      this.copySuccess.set(false);
    }, 2000);
  }

  protected async onVerify(): Promise<void> {
    const enrollment: TotpEnrollment | null = this.enrollment();

    if (!enrollment || this.code().length !== 6) {
      return;
    }

    this.errorMessage.set(null);
    this.verifyingCode.set(true);

    try {
      const { backupCodes } = await this.authStore.verifyEnrollment(enrollment.mfaMethodId, this.code());
      this.backupCodes.set(backupCodes);
      this.step.set('backup-codes');
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error));
    } finally {
      this.verifyingCode.set(false);
    }
  }

  protected finish(): void {
    this.dialogRef.close();
  }
}
