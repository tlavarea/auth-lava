import { Component, inject, Signal, signal, WritableSignal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { UserResponse } from '@core/auth/auth.models';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmSpinnerImports],
  template: `
    <div class="flex min-h-dvh items-center justify-center p-4">
      <div hlmCard class="w-full max-w-sm">
        <div hlmCardHeader>
          <h1 hlmCardTitle>Dashboard</h1>
          <p hlmCardDescription>You're signed in.</p>
        </div>
        <div hlmCardContent class="flex flex-col gap-4">
          @if (user(); as user) {
            <dl class="flex flex-col gap-2 text-sm">
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Email</dt>
                <dd>{{ user.email }}</dd>
              </div>
              <div class="flex items-center justify-between gap-2">
                <dt class="text-muted-foreground">Email verified</dt>
                <dd>
                  @if (user.emailVerified) {
                    <span hlmBadge>Verified</span>
                  } @else {
                    <span hlmBadge variant="secondary">Unverified</span>
                  }
                </dd>
              </div>
            </dl>
          }

          @if (user()?.mfaEnabled) {
            <a hlmBtn variant="outline" routerLink="/mfa/disable">Disable two-factor authentication</a>
          } @else {
            <a hlmBtn variant="outline" routerLink="/mfa/enroll">Set up two-factor authentication</a>
          }

          <button hlmBtn variant="destructive" type="button" [disabled]="loggingOut()" (click)="onLogout()">
            @if (loggingOut()) {
              <hlm-spinner />
              Signing out...
            } @else {
              Sign out
            }
          </button>
        </div>
      </div>
    </div>
  `,
})
export class DashboardPage {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly router: Router = inject(Router);

  protected readonly user: Signal<UserResponse | null> = this.authStore.user;
  protected readonly loggingOut: WritableSignal<boolean> = signal(false);

  protected async onLogout(): Promise<void> {
    this.loggingOut.set(true);
    try {
      await this.authStore.logout();
    } finally {
      this.loggingOut.set(false);
    }
    await this.router.navigateByUrl('/login');
  }
}
