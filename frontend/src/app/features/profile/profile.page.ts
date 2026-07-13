import { Component, computed, DestroyRef, inject, Signal, signal, WritableSignal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';

import { UserResponse } from '@core/auth/auth.models';
import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { MfaDisableDialog } from '@features/mfa-disable/mfa-disable.dialog';
import { ChangeEmailForm } from '@features/profile/form/change-email/change-email.form';
import { ChangePasswordForm } from '@features/profile/form/change-password/change-password.form';
import { EmailChangeStep } from '@models/models';
import { Card } from '@shared/card/card';
import { HlmDialogService } from '@spartan-ng/helm/dialog';

@Component({
  selector: 'app-profile',
  imports: [Card, ChangeEmailForm, ChangePasswordForm, HlmBadgeImports, HlmButtonImports, RouterLink],
  template: `
    <div class="mx-auto flex w-full max-w-sm flex-col gap-4 p-4 sm:max-w-xl lg:max-w-2xl">
      <app-card contentClass="flex flex-col gap-2 text-sm" description="Manage your account details" title="Profile">
        @if (user(); as user) {
          <div class="flex items-center justify-between gap-2">
            <span class="text-muted-foreground">Email</span>
            <span>{{ user.email }}</span>
          </div>
          <div class="flex items-center justify-between gap-2">
            <span class="text-muted-foreground">Email verified</span>
            @if (user.emailVerified) {
              <span hlmBadge>Verified</span>
            } @else {
              <span hlmBadge variant="secondary">Unverified</span>
            }
          </div>
        }
      </app-card>

      <app-card description="Changing your password signs you out of every other device" title="Change password">
        <app-change-password />
      </app-card>

      <app-card
        contentClass="flex max-w-sm flex-col gap-4"
        title="Change email"
        [description]="emailChangeCardDescription()">
        <app-change-email
          (pendingNewEmailChange)="onPendingEmailChange($event)"
          (stepChange)="onEmailChangeStep($event)" />
      </app-card>

      <app-card
        contentClass="flex flex-row justify-between"
        description="Adds an extra layer of protection to your account"
        title="Two-factor authentication">
        <p class="flex items-center">
          Two-factor verification is:
          <span
            class="relative top-px ml-1 text-base font-bold"
            [class]="{ 'text-green-800': user()?.mfaEnabled, 'text-destructive': !user()?.mfaEnabled }">
            {{ user()?.mfaEnabled ? 'ON' : 'OFF' }}
          </span>
        </p>
        <button type="button" hlmBtn (click)="onChangeMfa()">Update</button>
      </app-card>
    </div>
  `,
})
export class ProfilePage {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly dialogService: HlmDialogService = inject(HlmDialogService);

  protected readonly user: Signal<UserResponse | null> = this.authStore.user;

  protected readonly emailChangeStep: WritableSignal<EmailChangeStep> = signal<EmailChangeStep>('email');
  protected readonly onEmailChangeStep: (value: EmailChangeStep) => void = (value: EmailChangeStep): void => {
    this.emailChangeStep.set(value);
  };
  protected readonly emailChangeCardDescription: Signal<string> = computed((): string => {
    return this.emailChangeStep() === 'email'
      ? "We'll send a verification code to your new address."
      : `Enter the code we sent to ${this.pendingNewEmail()}.`;
  });
  protected readonly pendingNewEmail: WritableSignal<string> = signal('');
  protected readonly onPendingEmailChange: (value: string) => void = (value: string): void => {
    this.pendingNewEmail.set(value);
  };
  protected readonly onChangeMfa: () => void = (): void => {
    if (!this.user()?.mfaEnabled) {
      return;
    }
    this.dialogService.open(MfaDisableDialog, { showCloseButton: false });
  };
}
