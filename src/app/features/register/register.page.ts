import { Component, inject, signal } from '@angular/core';
import { email, form, FormField, FormRoot, maxLength, minLength, required } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore } from '../../core/auth/auth.store';
import { extractErrorMessage } from '../../core/auth/extract-error-message';
import { OauthProviders } from '../../core/auth/oauth-providers/oauth-providers';

@Component({
  selector: 'app-register',
  imports: [
    FormField,
    FormRoot,
    RouterLink,
    HlmAlertImports,
    HlmButton,
    HlmCardImports,
    HlmFieldImports,
    HlmInput,
    HlmSpinnerImports,
    OauthProviders,
  ],
  template: `
    <div class="flex min-h-dvh items-center justify-center p-4">
      <div class="w-full max-w-sm" hlmCard>
        <div hlmCardHeader>
          <h1 hlmCardTitle>Create an account</h1>
          <p hlmCardDescription>Enter your email and choose a password.</p>
        </div>
        <div class="flex flex-col gap-4" hlmCardContent>
          @if (oauthErrorMessage()) {
            <div hlmAlert variant="destructive">
              <p hlmAlertDescription>{{ oauthErrorMessage() }}</p>
            </div>
          }

          <form class="flex flex-col gap-4" [formRoot]="registerForm">
            @for (error of registerForm().errors(); track error.kind) {
              <div hlmAlert variant="destructive">
                <p hlmAlertDescription>{{ error.message }}</p>
              </div>
            }

            <div hlmField>
              <label hlmFieldLabel for="email">Email</label>
              <input hlmInput id="email" type="email" autocomplete="email" [formField]="registerForm.email" />
              @for (error of registerForm.email().errors(); track error.kind) {
                <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
              }
            </div>

            <div hlmField>
              <label hlmFieldLabel for="password">Password</label>
              <input
                hlmInput
                id="password"
                type="password"
                autocomplete="new-password"
                [formField]="registerForm.password" />
              @for (error of registerForm.password().errors(); track error.kind) {
                <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
              }
              <p hlmFieldDescription>Must be 8-32 characters.</p>
            </div>

            <button hlmBtn type="submit" [disabled]="registerForm().submitting()">
              @if (registerForm().submitting()) {
                <hlm-spinner />
                Creating account...
              } @else {
                Create account
              }
            </button>
          </form>

          <app-oauth-providers />
        </div>
        <div class="justify-center" hlmCardFooter>
          <p class="text-sm text-muted-foreground">
            Already have an account?
            <a class="text-primary underline-offset-4 hover:underline" routerLink="/login">Sign in</a>
          </p>
        </div>
      </div>
    </div>
  `,
})
export class RegisterPage {
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly model = signal({ email: '', password: '' });

  protected readonly registerForm = form(
    this.model,
    (path) => {
      required(path.email, { message: 'Email is required.' });
      email(path.email, { message: 'Enter a valid email address.' });
      required(path.password, { message: 'Password is required.' });
      minLength(path.password, 8, { message: 'Password must be at least 8 characters.' });
      maxLength(path.password, 32, { message: 'Password must be at most 32 characters.' });
    },
    {
      submission: {
        action: async (field) => {
          try {
            await this.authStore.register(field().value());
          } catch (error) {
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }
          await this.router.navigateByUrl('/login?registered=1');
          return;
        },
      },
    }
  );

  protected readonly oauthErrorMessage = signal(
    this.route.snapshot.queryParamMap.get('error') === 'oauth'
      ? 'Sign-up with that provider failed. Please try again.'
      : null
  );
}
