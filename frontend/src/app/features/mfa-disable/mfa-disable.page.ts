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

type DisableFormModel = {
  code: string;
};

@Component({
  selector: 'app-mfa-disable',
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
    <div class="flex h-full items-center justify-center p-4">
      <div hlmCard class="w-full max-w-sm">
        <div hlmCardHeader>
          <h1 hlmCardTitle>Disable two-factor authentication</h1>
          <p hlmCardDescription>Enter the code from your authenticator app, or a backup code, to confirm.</p>
        </div>
        <div hlmCardContent>
          <form class="flex flex-col gap-4" [formRoot]="disableForm">
            @for (error of disableForm().errors(); track error.kind) {
              <div hlmAlert variant="destructive">
                <p hlmAlertDescription>{{ error.message }}</p>
              </div>
            }

            <div hlmField>
              @if (!enterBackupCode()) {
                <div class="flex items-center justify-between">
                  <label hlmFieldLabel for="otp">Verification code</label>
                  @if (disableForm().submitting()) {
                    <hlm-spinner />
                  } @else {
                    <button hlmBtn size="xs" type="button" variant="outline" (click)="enterBackupCode.set(true)">
                      Enter backup code
                    </button>
                  }
                </div>
                <app-otp-input
                  inputId="otp"
                  [disabled]="disableForm().submitting()"
                  [maxLength]="6"
                  [value]="model().code"
                  (valueChange)="updateFormModel($event)"
                  (completed)="disableMfa()" />
              } @else {
                <label hlmFieldLabel for="backupCode">Code</label>
                <input
                  hlmInput
                  id="backupCode"
                  type="text"
                  autocomplete="backup-code"
                  inputmode="text"
                  [formField]="disableForm.code" />
                @for (error of disableForm.code().errors(); track error.kind) {
                  <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
                }
              }
            </div>

            <div class="flex gap-2">
              <button hlmBtn type="submit" variant="destructive" [disabled]="disableForm().submitting()">
                @if (disableForm().submitting()) {
                  <hlm-spinner />
                  Disabling...
                } @else {
                  Disable two-factor authentication
                }
              </button>
              <button hlmBtn type="button" variant="outline" [disabled]="disableForm().submitting()" (click)="cancel()">
                Cancel
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `,
})
export class MfaDisablePage {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly router: Router = inject(Router);

  protected readonly enterBackupCode: WritableSignal<boolean> = signal(false);
  protected readonly model: WritableSignal<DisableFormModel> = signal({ code: '' });
  protected readonly updateFormModel: (code: string) => void = (code: string): void => {
    this.model.update((m: DisableFormModel): DisableFormModel => ({ ...m, code }));
  };

  protected readonly disableForm: FieldTree<DisableFormModel> = form(
    this.model,
    (path: SchemaPathTree<DisableFormModel>): void => {
      required(path.code, { message: 'Enter your verification code.' });
      minLength(path.code, 6, { message: 'Code must be 6-12 characters.' });
      maxLength(path.code, 12, { message: 'Code must be 6-12 characters.' });
    },
    {
      submission: {
        action: (field: FieldTree<DisableFormModel>): Promise<ValidationError | ValidationError[] | undefined> =>
          this.disableMfaWithCode(field().value().code),
      },
    }
  );

  protected readonly disableMfa: () => void = (): void => {
    submit(this.disableForm, (): Promise<ValidationError | ValidationError[] | undefined> =>
      this.disableMfaWithCode(this.model().code)
    );
  };

  protected async cancel(): Promise<void> {
    await this.router.navigateByUrl('/');
  }

  private async disableMfaWithCode(code: string): Promise<ValidationError | ValidationError[] | undefined> {
    try {
      await this.authStore.disableMfa(code);
    } catch (error) {
      this.updateFormModel('');
      return { kind: 'serverError', message: extractErrorMessage(error) };
    }

    await this.router.navigateByUrl('/');
    return;
  }
}
