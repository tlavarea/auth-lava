import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { AuthStore } from '../../core/auth/auth.store';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, HlmBadgeImports, HlmButton, HlmCardImports, HlmSpinnerImports],
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

          <a hlmBtn variant="outline" routerLink="/mfa/enroll">Set up two-factor authentication</a>

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
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly user = this.authStore.user;
  protected readonly loggingOut = signal(false);

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
