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
  ValidationError,
} from '@angular/forms/signals';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmDialogImports } from '@spartan-ng/helm/dialog';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { extractErrorMessage } from '@core/auth/extract-error-message';
import { MfaDisableStep } from '@models/models';
import { OtpInput } from '@shared/otp-input/otp-input';
import { HlmSeparatorImports } from '@spartan-ng/helm/separator';

type DisableFormModel = {
  code: string;
};

@Component({
  imports: [
    FormField,
    FormRoot,
    HlmAlertImports,
    HlmButtonImports,
    HlmDialogImports,
    HlmFieldImports,
    HlmInputImports,
    HlmSeparatorImports,
    HlmSpinnerImports,
    OtpInput,
  ],
  template: `
    @switch (step()) {
      @case ('confirm') {
        <hlm-dialog-header class="mb-8">
          <h3 hlmDialogTitle>Are you sure?</h3>
          <p hlmDialogDescription>
            This will remove the extra layer of security to your account. You will now only use your password to log in.
          </p>
        </hlm-dialog-header>
        <hlm-dialog-footer>
          <button hlmBtn variant="ghost" type="button" hlmDialogClose>Not now</button>
          <button hlmBtn type="button" (click)="advanceToApply()">Turn it off</button>
        </hlm-dialog-footer>
      }
      @case ('apply') {
        <hlm-dialog-header class="mb-8">
          <h3 hlmDialogTitle>Disable two-factor authentication</h3>
          <p hlmDialogDescription>Enter the code from your authenticator app, or a backup code, to confirm.</p>
        </hlm-dialog-header>
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
                (valueChange)="updateFormModel($event)" />
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

          <hlm-dialog-footer>
            <button hlmBtn type="button" variant="ghost" hlmDialogClose [disabled]="disableForm().submitting()">
              Cancel
            </button>
            <button hlmBtn type="submit" variant="destructive" [disabled]="disableForm().submitting()">
              @if (disableForm().submitting()) {
                <hlm-spinner />
                Disabling...
              } @else {
                Disable two-factor authentication
              }
            </button>
          </hlm-dialog-footer>
        </form>
      }
    }
  `,
})
export class MfaDisableDialog {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly dialogRef: BrnDialogRef = inject(BrnDialogRef);

  protected readonly step: WritableSignal<MfaDisableStep> = signal<MfaDisableStep>('confirm');
  protected readonly advanceToApply: () => void = (): void => {
    this.step.set('apply');
  };

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
        action: async (
          field: FieldTree<DisableFormModel>
        ): Promise<ValidationError | ValidationError[] | undefined> => {
          try {
            await this.authStore.disableMfa(field().value().code);
          } catch (error) {
            this.updateFormModel('');
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }

          this.dialogRef.close();
          return;
        },
      },
    }
  );
}
