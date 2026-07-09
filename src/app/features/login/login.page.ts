import { Component, inject, signal } from '@angular/core';
import { email, form, FormField, FormRoot, required } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore } from '../../core/auth/auth.store';
import { extractErrorMessage } from '../../core/auth/extract-error-message';
import { OauthProviders } from '../../core/auth/oauth-providers/oauth-providers';

@Component({
  selector: 'app-login',
  imports: [
    FormField,
    FormRoot,
    RouterLink,
    HlmAlertImports,
    HlmButtonImports,
    HlmCardImports,
    HlmFieldImports,
    HlmInputImports,
    HlmSpinnerImports,
    OauthProviders,
  ],
  template: `
    <div class="flex min-h-dvh items-center justify-center p-4">
      <div class="w-full max-w-sm" hlmCard>
        <div hlmCardHeader>
          <h1 class="text-center" hlmCardTitle>Sign in</h1>
          <p class="text-center" hlmCardDescription>Enter your email and password to continue.</p>
        </div>
        <div class="flex flex-col gap-4" hlmCardContent>
          <app-oauth-providers />

          @if (oauthErrorMessage()) {
            <div hlmAlert variant="destructive">
              <p hlmAlertDescription>{{ oauthErrorMessage() }}</p>
            </div>
          }
          @if (registeredMessage()) {
            <div hlmAlert>
              <p hlmAlertDescription>{{ registeredMessage() }}</p>
            </div>
          }

          <form class="flex flex-col gap-4" [formRoot]="loginForm">
            @for (error of loginForm().errors(); track error.kind) {
              <div hlmAlert variant="destructive">
                <p hlmAlertDescription>{{ error.message }}</p>
              </div>
            }

            <div hlmField>
              <label hlmFieldLabel for="email">Email</label>
              <input hlmInput id="email" type="email" autocomplete="email" [formField]="loginForm.email" />
              @for (error of loginForm.email().errors(); track error.kind) {
                <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
              }
            </div>

            <div hlmField>
              <label hlmFieldLabel for="password">Password</label>
              <input
                hlmInput
                id="password"
                type="password"
                autocomplete="current-password"
                [formField]="loginForm.password" />
              @for (error of loginForm.password().errors(); track error.kind) {
                <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
              }
            </div>

            <button hlmBtn type="submit" [disabled]="loginForm().submitting()">
              @if (loginForm().submitting()) {
                <hlm-spinner />
                Signing in...
              } @else {
                Sign in
              }
            </button>
          </form>
        </div>
        <div class="justify-center" hlmCardFooter>
          <p class="text-sm text-muted-foreground">
            Don't have an account?
            <a class="text-primary underline-offset-4 hover:underline" routerLink="/register">Sign up</a>
          </p>
        </div>
      </div>
    </div>
  `,
})
export class LoginPage {
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly model = signal({ email: '', password: '' });

  protected readonly loginForm = form(
    this.model,
    (path) => {
      required(path.email, { message: 'Email is required.' });
      email(path.email, { message: 'Enter a valid email address.' });
      required(path.password, { message: 'Password is required.' });
    },
    {
      submission: {
        action: async (field) => {
          try {
            await this.authStore.login(field().value());
          } catch (error) {
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }
          await this.router.navigateByUrl(this.authStore.status() === 'mfa-pending' ? '/mfa/verify' : '/');
          return;
        },
      },
    }
  );

  protected readonly oauthErrorMessage = signal(
    this.route.snapshot.queryParamMap.get('error') === 'oauth'
      ? 'Sign-in with that provider failed. Please try again.'
      : null
  );

  protected readonly registeredMessage = signal(
    this.route.snapshot.queryParamMap.get('registered') === '1' ? 'Account created. Sign in to continue.' : null
  );
}
