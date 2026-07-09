import { Component, inject, signal } from '@angular/core';
import { form, FormField, FormRoot, maxLength, minLength, required } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore } from '../../core/auth/auth.store';
import { extractErrorMessage } from '../../core/auth/extract-error-message';

@Component({
  selector: 'app-mfa-verify',
  imports: [
    FormField,
    FormRoot,
    HlmAlertImports,
    HlmButton,
    HlmCardImports,
    HlmFieldImports,
    HlmInput,
    HlmSpinnerImports,
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
              <label hlmFieldLabel for="code">Code</label>
              <input
                hlmInput
                id="code"
                type="text"
                autocomplete="one-time-code"
                inputmode="text"
                [formField]="verifyForm.code" />
              @for (error of verifyForm.code().errors(); track error.kind) {
                <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
              }
            </div>

            <button hlmBtn type="submit" [disabled]="verifyForm().submitting()">
              @if (verifyForm().submitting()) {
                <hlm-spinner />
                Verifying...
              } @else {
                Verify
              }
            </button>
          </form>
        </div>
      </div>
    </div>
  `,
})
export class MfaVerifyPage {
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly model = signal({ code: '' });

  protected readonly verifyForm = form(
    this.model,
    (path) => {
      required(path.code, { message: 'Enter your verification code.' });
      minLength(path.code, 6, { message: 'Code must be 6-12 characters.' });
      maxLength(path.code, 12, { message: 'Code must be 6-12 characters.' });
    },
    {
      submission: {
        action: async (field) => {
          try {
            await this.authStore.verifyMfa(field().value().code);
          } catch (error) {
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }
          await this.router.navigateByUrl('/');
          return;
        },
      },
    }
  );
}
