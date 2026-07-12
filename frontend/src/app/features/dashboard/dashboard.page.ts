import { Component, inject, Signal } from '@angular/core';
import { UserResponse } from '@core/auth/auth.models';
import { HlmCardImports } from '@spartan-ng/helm/card';

import { AuthStore, AuthStoreType } from '@core/auth/auth.store';

@Component({
  selector: 'app-dashboard',
  imports: [HlmCardImports],
  template: `
    <div class="flex h-full items-center justify-center p-4">
      <div hlmCard class="w-full max-w-sm">
        <div hlmCardHeader>
          <h1 hlmCardTitle>Dashboard</h1>
          @if (user(); as user) {
            <p hlmCardDescription>Welcome back, {{ user.email }}.</p>
          } @else {
            <p hlmCardDescription>Welcome back.</p>
          }
        </div>
      </div>
    </div>
  `,
})
export class DashboardPage {
  private readonly authStore: AuthStoreType = inject(AuthStore);

  protected readonly user: Signal<UserResponse | null> = this.authStore.user;
}
