import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import {
  ChildFieldContext,
  email,
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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BrnInputOtp } from '@spartan-ng/brain/input-otp';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmInputOtpImports } from '@spartan-ng/helm/input-otp';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { extractErrorMessage } from '@core/auth/extract-error-message';
import { OauthProviders } from '@core/auth/oauth-providers/oauth-providers';

type EmailFormModel = {
  email: string;
};

type PasswordFormModel = {
  password: string;
  confirmPassword: string;
};

type Step = 'email' | 'code' | 'password';

const CODE_TTL_SECONDS = 5 * 60;
const RESEND_COOLDOWN_SECONDS = 60;

@Component({
  selector: 'app-register',
  imports: [
    BrnInputOtp,
    FormField,
    FormRoot,
    RouterLink,
    HlmAlertImports,
    HlmButtonImports,
    HlmCardImports,
    HlmFieldImports,
    HlmInputImports,
    HlmInputOtpImports,
    HlmSpinnerImports,
    OauthProviders,
  ],
  template: `
    <div class="flex min-h-dvh items-center justify-center p-4">
      <div class="w-full max-w-sm" hlmCard>
        <div hlmCardHeader>
          <h1 class="text-center" hlmCardTitle>Create an account</h1>
          <p class="text-center" hlmCardDescription>
            @switch (step()) {
              @case ('email') {
                Enter your email to get started.
              }
              @case ('code') {
                Enter the code we sent to {{ email() }}.
              }
              @case ('password') {
                Choose a password for your account.
              }
            }
          </p>
        </div>
        <div class="flex flex-col gap-4" hlmCardContent>
          <app-oauth-providers />

          @if (oauthErrorMessage()) {
            <div hlmAlert variant="destructive">
              <p hlmAlertDescription>{{ oauthErrorMessage() }}</p>
            </div>
          }
          @if (errorMessage()) {
            <div hlmAlert variant="destructive">
              <p hlmAlertDescription>{{ errorMessage() }}</p>
            </div>
          }

          @switch (step()) {
            @case ('email') {
              <form class="flex flex-col gap-4" [formRoot]="emailForm">
                @for (error of emailForm().errors(); track error.kind) {
                  <div hlmAlert variant="destructive">
                    <p hlmAlertDescription>{{ error.message }}</p>
                  </div>
                }

                <div hlmField>
                  <label hlmFieldLabel for="email">Email</label>
                  <input hlmInput id="email" type="email" autocomplete="email" [formField]="emailForm.email" />
                  @for (error of emailForm.email().errors(); track error.kind) {
                    <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
                  }
                </div>

                <button hlmBtn type="submit" [disabled]="emailForm().submitting()">
                  @if (emailForm().submitting()) {
                    <hlm-spinner />
                    Sending code...
                  } @else {
                    Continue
                  }
                </button>
              </form>
            }
            @case ('code') {
              <div hlmField class="items-center">
                <label hlmFieldLabel for="otp">Verification code</label>
                <brn-input-otp
                  id="otp"
                  hlmInputOtp
                  [maxLength]="6"
                  [disabled]="codeSecondsRemaining() === 0"
                  [value]="code()"
                  (valueChange)="code.set($event)"
                  (completed)="onVerifyCode()">
                  <hlm-input-otp-group>
                    <hlm-input-otp-slot [index]="0" />
                    <hlm-input-otp-slot [index]="1" />
                    <hlm-input-otp-slot [index]="2" />
                    <hlm-input-otp-slot [index]="3" />
                    <hlm-input-otp-slot [index]="4" />
                    <hlm-input-otp-slot [index]="5" />
                  </hlm-input-otp-group>
                </brn-input-otp>
                <p hlmFieldDescription>
                  @if (codeSecondsRemaining() > 0) {
                    Code expires in {{ formattedCodeCountdown() }}.
                  } @else {
                    This code has expired. Request a new one below.
                  }
                </p>
              </div>

              <button
                hlmBtn
                type="button"
                [disabled]="code().length !== 6 || codeSecondsRemaining() === 0 || verifyingCode()"
                (click)="onVerifyCode()">
                @if (verifyingCode()) {
                  <hlm-spinner />
                  Verifying...
                } @else {
                  Verify
                }
              </button>

              <button
                hlmBtn
                variant="outline"
                type="button"
                [disabled]="resendSecondsRemaining() > 0 || resending()"
                (click)="onResendCode()">
                @if (resendSecondsRemaining() > 0) {
                  Resend code in {{ resendSecondsRemaining() }}s
                } @else {
                  Resend code
                }
              </button>
            }
            @case ('password') {
              <form class="flex flex-col gap-4" [formRoot]="passwordForm">
                @for (error of passwordForm().errors(); track error.kind) {
                  <div hlmAlert variant="destructive">
                    <p hlmAlertDescription>{{ error.message }}</p>
                  </div>
                }

                <div hlmField>
                  <label hlmFieldLabel for="password">Password</label>
                  <input
                    hlmInput
                    id="password"
                    type="password"
                    autocomplete="new-password"
                    [formField]="passwordForm.password" />
                  @for (error of passwordForm.password().errors(); track error.kind) {
                    <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
                  }
                  <p hlmFieldDescription>Must be 8-32 characters.</p>
                </div>

                <div hlmField>
                  <label hlmFieldLabel for="confirmPassword">Confirm password</label>
                  <input
                    hlmInput
                    id="confirmPassword"
                    type="password"
                    autocomplete="new-password"
                    [formField]="passwordForm.confirmPassword" />
                  @for (error of passwordForm.confirmPassword().errors(); track error.kind) {
                    <hlm-field-error [validator]="error.kind">{{ error.message }}</hlm-field-error>
                  }
                </div>

                <button hlmBtn type="submit" [disabled]="passwordForm().submitting()">
                  @if (passwordForm().submitting()) {
                    <hlm-spinner />
                    Creating account...
                  } @else {
                    Create account
                  }
                </button>
              </form>
            }
          }
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
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly router: Router = inject(Router);
  private readonly route: ActivatedRoute = inject(ActivatedRoute);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  private registrationToken: string | null = null;
  private tickIntervalId: ReturnType<typeof setInterval> | null = null;

  protected readonly step: WritableSignal<Step> = signal<Step>('email');
  protected readonly email: WritableSignal<string> = signal('');
  protected readonly errorMessage: WritableSignal<string | null> = signal<string | null>(null);

  protected readonly emailModel: WritableSignal<EmailFormModel> = signal({ email: '' });
  protected readonly emailForm: FieldTree<EmailFormModel> = form(
    this.emailModel,
    (path: SchemaPathTree<EmailFormModel>): void => {
      required(path.email, { message: 'Email is required.' });
      email(path.email, { message: 'Enter a valid email address.' });
    },
    {
      submission: {
        action: async (field: FieldTree<EmailFormModel>): Promise<ValidationError | ValidationError[] | undefined> => {
          try {
            await this.authStore.startRegistration(field().value().email);
          } catch (error) {
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }
          this.email.set(field().value().email);
          this.enterCodeStep();
          return;
        },
      },
    }
  );

  protected readonly code: WritableSignal<string> = signal('');
  protected readonly verifyingCode: WritableSignal<boolean> = signal(false);
  protected readonly resending: WritableSignal<boolean> = signal(false);
  protected readonly codeSecondsRemaining: WritableSignal<number> = signal(CODE_TTL_SECONDS);
  protected readonly resendSecondsRemaining: WritableSignal<number> = signal(RESEND_COOLDOWN_SECONDS);

  protected readonly passwordModel: WritableSignal<PasswordFormModel> = signal({ password: '', confirmPassword: '' });
  protected readonly passwordForm: FieldTree<PasswordFormModel> = form(
    this.passwordModel,
    (path: SchemaPathTree<PasswordFormModel>): void => {
      required(path.password, { message: 'Password is required.' });
      minLength(path.password, 8, { message: 'Password must be at least 8 characters.' });
      maxLength(path.password, 32, { message: 'Password must be at most 32 characters.' });
      required(path.confirmPassword, { message: 'Please confirm your password.' });
      validate(
        path.confirmPassword,
        ({ value, valueOf }: ChildFieldContext<string>): ValidationError | ValidationError[] | undefined =>
          value() === valueOf(path.password) ? undefined : { kind: 'mismatch', message: 'Passwords do not match.' }
      );
    },
    {
      submission: {
        action: async (
          field: FieldTree<PasswordFormModel>
        ): Promise<ValidationError | ValidationError[] | undefined> => {
          if (!this.registrationToken) {
            this.step.set('email');
            return { kind: 'serverError', message: 'Your session expired. Please start over.' };
          }

          try {
            await this.authStore.completeRegistration(this.registrationToken, field().value().password);
          } catch (error) {
            return { kind: 'serverError', message: extractErrorMessage(error) };
          }

          await this.router.navigateByUrl('/login?registered=1');
          return;
        },
      },
    }
  );

  protected readonly oauthErrorMessage: WritableSignal<string | null> = signal(
    this.route.snapshot.queryParamMap.get('error') === 'oauth'
      ? 'Sign-up with that provider failed. Please try again.'
      : null
  );

  constructor() {
    this.destroyRef.onDestroy((): void => this.clearTimers());
  }

  protected formattedCodeCountdown(): string {
    const totalSeconds: number = this.codeSecondsRemaining();
    const minutes: number = Math.floor(totalSeconds / 60);
    const seconds: number = totalSeconds % 60;
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  protected async onVerifyCode(): Promise<void> {
    if (this.code().length !== 6 || this.verifyingCode() || this.codeSecondsRemaining() === 0) {
      return;
    }

    this.errorMessage.set(null);
    this.verifyingCode.set(true);

    try {
      this.registrationToken = await this.authStore.verifyRegistrationCode(this.email(), this.code());
      this.clearTimers();
      this.step.set('password');
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error));
    } finally {
      this.verifyingCode.set(false);
    }
  }

  protected async onResendCode(): Promise<void> {
    if (this.resendSecondsRemaining() > 0 || this.resending()) {
      return;
    }

    this.errorMessage.set(null);
    this.resending.set(true);

    try {
      await this.authStore.startRegistration(this.email());
      this.code.set('');
      this.startTimers();
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error));
    } finally {
      this.resending.set(false);
    }
  }

  private enterCodeStep(): void {
    this.step.set('code');
    this.startTimers();
  }

  private startTimers(): void {
    this.clearTimers();
    this.codeSecondsRemaining.set(CODE_TTL_SECONDS);
    this.resendSecondsRemaining.set(RESEND_COOLDOWN_SECONDS);
    this.tickIntervalId = setInterval((): void => {
      this.codeSecondsRemaining.update((seconds) => Math.max(0, seconds - 1));
      this.resendSecondsRemaining.update((seconds) => Math.max(0, seconds - 1));
    }, 1000);
  }

  private clearTimers(): void {
    if (this.tickIntervalId !== null) {
      clearInterval(this.tickIntervalId);
      this.tickIntervalId = null;
    }
  }
}
