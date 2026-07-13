import { Component, inject, output, OutputEmitterRef, signal, WritableSignal } from '@angular/core';
import {
  email,
  FieldTree,
  form,
  FormField,
  FormRoot,
  required,
  SchemaPathTree,
  ValidationError,
} from '@angular/forms/signals';
import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { extractErrorMessage } from '@core/auth/extract-error-message';
import { EmailChangeStep } from '@models/models';
import { OtpInput } from '@shared/otp-input/otp-input';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

type EmailChangeFormModel = {
  newEmail: string;
};

@Component({
  selector: 'app-change-email',
  imports: [
    FormField,
    FormRoot,
    HlmAlertImports,
    HlmButtonImports,
    HlmFieldImports,
    HlmInputImports,
    HlmSpinnerImports,
    OtpInput,
  ],
  template: `
    @if (emailChanged()) {
      <div hlmAlert>
        <p hlmAlertDescription>Email changed successfully.</p>
      </div>
    }

    @switch (emailChangeStep()) {
      @case ('email') {
        <form class="flex flex-col gap-4" [formRoot]="emailChangeForm">
          @for (error of emailChangeForm().errors(); track error.kind) {
            <div hlmAlert variant="destructive">
              <p hlmAlertDescription>{{ error.message }}</p>
            </div>
          }

          <div hlmField>
            <label hlmFieldLabel for="newEmail">New email</label>
            <input hlmInput id="newEmail" type="email" autocomplete="email" [formField]="emailChangeForm.newEmail" />
            @for (error of emailChangeForm.newEmail().errors(); track error.kind) {
              <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
            }
          </div>

          <button class="self-start" hlmBtn type="submit" [disabled]="emailChangeForm().submitting()">
            @if (emailChangeForm().submitting()) {
              <hlm-spinner />
              Sending code...
            } @else {
              Send verification code
            }
          </button>
        </form>
      }
      @case ('code') {
        @if (emailChangeCodeError()) {
          <div hlmAlert variant="destructive">
            <p hlmAlertDescription>{{ emailChangeCodeError() }}</p>
          </div>
        }

        <div hlmField class="items-center">
          <div class="flex items-center justify-center">
            <label hlmFieldLabel for="emailChangeCode">Verification code</label>
            @if (verifyingEmailChangeCode()) {
              <hlm-spinner />
            }
          </div>
          <app-otp-input
            inputId="emailChangeCode"
            [disabled]="verifyingEmailChangeCode()"
            [maxLength]="6"
            (valueChange)="emailChangeCode.set($event)"
            (completed)="onVerifyEmailChangeCode()" />
        </div>

        <div class="flex gap-2">
          <button
            hlmBtn
            type="button"
            variant="outline"
            [disabled]="resendingEmailChangeCode()"
            (click)="resendEmailChangeCode()">
            @if (resendingEmailChangeCode()) {
              <hlm-spinner />
            }
            Resend code
          </button>
          <button hlmBtn type="button" variant="ghost" (click)="cancelEmailChange()">Use a different email</button>
        </div>
      }
    }
  `,
  styles: ``,
})
export class ChangeEmailForm {
  private readonly authStore: AuthStoreType = inject(AuthStore);

  readonly stepChange: OutputEmitterRef<EmailChangeStep> = output<EmailChangeStep>();
  readonly pendingNewEmailChange: OutputEmitterRef<string> = output<string>();

  protected readonly emailChanged: WritableSignal<boolean> = signal(false);
  protected readonly emailChangeModel: WritableSignal<EmailChangeFormModel> = signal({ newEmail: '' });
  protected readonly emailChangeStep: WritableSignal<EmailChangeStep> = signal<EmailChangeStep>('email');
  protected readonly pendingNewEmail: WritableSignal<string> = signal('');
  protected readonly emailChangeCode: WritableSignal<string> = signal('');
  protected readonly verifyingEmailChangeCode: WritableSignal<boolean> = signal(false);
  protected readonly resendingEmailChangeCode: WritableSignal<boolean> = signal(false);
  protected readonly emailChangeCodeError: WritableSignal<string | null> = signal(null);

  protected readonly emailChangeForm: FieldTree<EmailChangeFormModel> = form(
    this.emailChangeModel,
    (path: SchemaPathTree<EmailChangeFormModel>): void => {
      required(path.newEmail, { message: 'Enter a new email address.' });
      email(path.newEmail, { message: 'Enter a valid email address.' });
    },
    {
      submission: {
        action: async (
          field: FieldTree<EmailChangeFormModel>
        ): Promise<ValidationError | ValidationError[] | undefined> => {
          const newEmail: string = field().value().newEmail;

          try {
            await this.authStore.startEmailChange(newEmail);
          } catch (error) {
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }

          this.pendingNewEmail.set(newEmail);
          this.pendingNewEmailChange.emit(newEmail);
          this.emailChangeStep.set('code');
          this.stepChange.emit('code');
          return;
        },
      },
    }
  );

  protected cancelEmailChange(): void {
    this.emailChangeStep.set('email');
    this.stepChange.emit('email');
    this.emailChangeCodeError.set(null);
    this.emailChangeCode.set('');
  }

  protected async onVerifyEmailChangeCode(): Promise<void> {
    if (this.emailChangeCode().length !== 6) {
      return;
    }

    this.emailChangeCodeError.set(null);
    this.verifyingEmailChangeCode.set(true);

    try {
      await this.authStore.verifyEmailChange(this.emailChangeCode());
      this.emailChangeStep.set('email');
      this.stepChange.emit('email');
      this.emailChangeCode.set('');
      this.emailChangeForm().reset({ newEmail: '' });
      this.emailChanged.set(true);
      setTimeout((): void => this.emailChanged.set(false), 3000);
    } catch (error) {
      this.emailChangeCodeError.set(extractErrorMessage(error));
      this.emailChangeCode.set('');
    } finally {
      this.verifyingEmailChangeCode.set(false);
    }
  }

  protected async resendEmailChangeCode(): Promise<void> {
    this.emailChangeCodeError.set(null);
    this.resendingEmailChangeCode.set(true);

    try {
      await this.authStore.startEmailChange(this.pendingNewEmail());
    } catch (error) {
      this.emailChangeCodeError.set(extractErrorMessage(error));
    } finally {
      this.resendingEmailChangeCode.set(false);
    }
  }
}
