import { Component, inject, signal, WritableSignal } from '@angular/core';
import {
  FieldTree,
  form,
  FormField,
  FormRoot,
  maxLength,
  minLength,
  required,
  SchemaPathTree,
  submit,
  ValidationError,
} from '@angular/forms/signals';
import { Router } from '@angular/router';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { extractErrorMessage } from '@core/auth/extract-error-message';
import { OtpInput } from '../../shared/otp-input/otp-input';

type VerifyFormModel = {
  code: string;
};

@Component({
  selector: 'app-mfa-verify',
  imports: [
    FormField,
    FormRoot,
    HlmAlertImports,
    HlmButtonImports,
    HlmCardImports,
    HlmFieldImports,
    HlmInputImports,
    HlmSpinnerImports,
    OtpInput,
  ],
  template: `
    <div class="flex min-h-dvh items-center justify-center p-4">
      <div hlmCard class="w-full max-w-sm">
        <div hlmCardHeader>
          <h1 hlmCardTitle>Two-factor verification</h1>
          <p hlmCardDescription>Enter the code from your authenticator app, or a backup code.</p>
        </div>
        <div hlmCardContent>
          <form class="flex flex-col gap-4" [formRoot]="verifyForm">
            @for (error of verifyForm().errors(); track error.kind) {
              <div hlmAlert variant="destructive">
                <p hlmAlertDescription>{{ error.message }}</p>
              </div>
            }

            <div hlmField>
              @if (!enterBackupCode()) {
                <div class="flex items-center justify-between">
                  <label hlmFieldLabel for="otp">Verification code</label>
                  @if (verifyForm().submitting()) {
                    <hlm-spinner />
                  } @else {
                    <button hlmBtn size="xs" type="button" variant="outline" (click)="enterBackupCode.set(true)">
                      Enter backup code
                    </button>
                  }
                </div>
                <app-otp-input
                  inputId="otp"
                  [disabled]="verifyForm().submitting()"
                  [maxLength]="6"
                  (valueChange)="updateFormModel($event)"
                  (completed)="verifyCode()" />
              } @else {
                <label hlmFieldLabel for="backupCode">Code</label>
                <input
                  hlmInput
                  id="backupCode"
                  type="text"
                  autocomplete="backup-code"
                  inputmode="text"
                  [formField]="verifyForm.code" />
                @for (error of verifyForm.code().errors(); track error.kind) {
                  <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
                }
              }
            </div>

            @if (enterBackupCode()) {
              <button hlmBtn type="submit" [disabled]="verifyForm().submitting()">
                @if (verifyForm().submitting()) {
                  <hlm-spinner />
                  Verifying...
                } @else {
                  Verify
                }
              </button>
            }
          </form>
        </div>
      </div>
    </div>
  `,
})
export class MfaVerifyPage {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly router: Router = inject(Router);

  protected readonly enterBackupCode: WritableSignal<boolean> = signal(false);
  protected readonly model: WritableSignal<VerifyFormModel> = signal({ code: '' });
  protected readonly updateFormModel: (code: string) => void = (code: string): void => {
    this.model.update((m: VerifyFormModel): VerifyFormModel => ({ ...m, code }));
  };

  protected readonly verifyForm: FieldTree<VerifyFormModel> = form(
    this.model,
    (path: SchemaPathTree<VerifyFormModel>): void => {
      required(path.code, { message: 'Enter your verification code.' });
      minLength(path.code, 6, { message: 'Code must be 6-12 characters.' });
      maxLength(path.code, 12, { message: 'Code must be 6-12 characters.' });
    },
    {
      submission: {
        action: (field: FieldTree<VerifyFormModel>): Promise<ValidationError | ValidationError[] | undefined> =>
          this.verifyMfa(field().value().code),
      },
    }
  );

  protected readonly verifyCode: () => void = (): void => {
    submit(this.verifyForm, (): Promise<ValidationError | ValidationError[] | undefined> =>
      this.verifyMfa(this.model().code)
    );
  };

  private async verifyMfa(code: string): Promise<ValidationError | ValidationError[] | undefined> {
    try {
      await this.authStore.verifyMfa(code);
    } catch (error) {
      return { kind: 'serverError', message: extractErrorMessage(error) };
    }
    await this.router.navigateByUrl('/');
    return;
  }
}
