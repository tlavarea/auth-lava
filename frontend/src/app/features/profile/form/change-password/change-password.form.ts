import { Component, inject, signal, WritableSignal } from '@angular/core';
import {
  ChildFieldContext,
  FieldTree,
  form,
  FormField,
  FormRoot,
  maxLength,
  minLength,
  required,
  SchemaPathTree,
  validate,
  ValidationError,
} from '@angular/forms/signals';
import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { extractErrorMessage } from '@core/auth/extract-error-message';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

type PasswordChangeFormModel = {
  currentPassword: string;
  newPassword: string;
  confirmNewPassword: string;
};

const emptyPasswordChangeModel: PasswordChangeFormModel = {
  currentPassword: '',
  newPassword: '',
  confirmNewPassword: '',
};

@Component({
  selector: 'app-change-password',
  imports: [
    FormField,
    FormRoot,
    HlmAlertImports,
    HlmButtonImports,
    HlmFieldImports,
    HlmInputImports,
    HlmSpinnerImports,
  ],
  template: `
    <form class="flex flex-col gap-4" [formRoot]="passwordForm">
      @for (error of passwordForm().errors(); track error.kind) {
        <div hlmAlert variant="destructive">
          <p hlmAlertDescription>{{ error.message }}</p>
        </div>
      }
      @if (passwordChanged()) {
        <div hlmAlert>
          <p hlmAlertDescription>Password changed successfully.</p>
        </div>
      }

      <div hlmField>
        <label hlmFieldLabel for="currentPassword">Current password</label>
        <input
          hlmInput
          id="currentPassword"
          type="password"
          autocomplete="current-password"
          [formField]="passwordForm.currentPassword" />
        @for (error of passwordForm.currentPassword().errors(); track error.kind) {
          <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
        }
      </div>

      <div hlmField>
        <label hlmFieldLabel for="newPassword">New password</label>
        <input
          hlmInput
          id="newPassword"
          type="password"
          autocomplete="new-password"
          [formField]="passwordForm.newPassword" />
        @for (error of passwordForm.newPassword().errors(); track error.kind) {
          <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
        }
        <p hlmFieldDescription>Must be 8-32 characters.</p>
      </div>

      <div hlmField>
        <label hlmFieldLabel for="confirmNewPassword">Confirm new password</label>
        <input
          hlmInput
          id="confirmNewPassword"
          type="password"
          autocomplete="new-password"
          [formField]="passwordForm.confirmNewPassword" />
        @for (error of passwordForm.confirmNewPassword().errors(); track error.kind) {
          <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
        }
      </div>

      <button class="self-start" hlmBtn type="submit" [disabled]="passwordForm().submitting()">
        @if (passwordForm().submitting()) {
          <hlm-spinner />
          Changing password...
        } @else {
          Change password
        }
      </button>
    </form>
  `,
  styles: ``,
})
export class ChangePasswordForm {
  private readonly authStore: AuthStoreType = inject(AuthStore);

  protected readonly passwordChanged: WritableSignal<boolean> = signal(false);
  protected readonly passwordModel: WritableSignal<PasswordChangeFormModel> = signal({ ...emptyPasswordChangeModel });
  protected readonly passwordForm: FieldTree<PasswordChangeFormModel> = form(
    this.passwordModel,
    (path: SchemaPathTree<PasswordChangeFormModel>): void => {
      required(path.currentPassword, { message: 'Enter your current password.' });
      required(path.newPassword, { message: 'Enter a new password.' });
      minLength(path.newPassword, 8, { message: 'Password must be 8-32 characters.' });
      maxLength(path.newPassword, 32, { message: 'Password must be 8-32 characters.' });
      required(path.confirmNewPassword, { message: 'Confirm your new password.' });
      validate(
        path.confirmNewPassword,
        ({ value, valueOf }: ChildFieldContext<string>): ValidationError | ValidationError[] | undefined =>
          value() === valueOf(path.newPassword) ? undefined : { kind: 'mismatch', message: 'Passwords do not match.' }
      );
    },
    {
      submission: {
        action: async (
          field: FieldTree<PasswordChangeFormModel>
        ): Promise<ValidationError | ValidationError[] | undefined> => {
          const { currentPassword, newPassword } = field().value();

          try {
            await this.authStore.changePassword(currentPassword, newPassword);
          } catch (error) {
            // reset() clears touched/dirty along with the value - a plain model patch would leave
            // the field touched, so the now-empty "current password" would immediately show its
            // own "required" error on top of the server error alert.
            this.passwordForm.currentPassword().reset('');
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }

          this.passwordForm().reset(emptyPasswordChangeModel);
          this.passwordChanged.set(true);
          setTimeout((): void => this.passwordChanged.set(false), 3000);
          return;
        },
      },
    }
  );
}
