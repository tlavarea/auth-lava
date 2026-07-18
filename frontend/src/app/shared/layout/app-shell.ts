import { Component, computed, inject, Signal, signal, WritableSignal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideLogOut, lucideUser } from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmDropdownMenuImports } from '@spartan-ng/helm/dropdown-menu';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { UserResponse } from '@core/auth/auth.models';
import { AuthStore, AuthStoreType } from '@core/auth/auth.store';
import { ThemeToggle } from '@shared/theme-toggle/theme-toggle';

@Component({
  selector: 'app-shell',
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    NgIcon,
    HlmAvatarImports,
    HlmButtonImports,
    HlmDropdownMenuImports,
    HlmSpinnerImports,
    ThemeToggle,
  ],
  viewProviders: [provideIcons({ lucideLogOut, lucideUser })],
  template: `
    <div class="flex h-dvh flex-col overflow-hidden">
      <header class="flex h-20 shrink-0 items-center justify-between border-b border-border px-4 shadow-sm">
        <nav class="flex items-center gap-8">
          <a routerLink="/" class="text-sm font-semibold">auth-lava</a>
          <div class="flex items-center gap-4">
            <a
              routerLink="/shipments"
              routerLinkActive="border-b-3 border-b-neutral-800 dark:border-b-neutral-400 dark:bg-neutral-900 bg-neutral-100 text-neutral-900 dark:text-neutral-50 font-medium"
              class="px-4 py-2 text-sm text-muted-foreground transition-colors hover:bg-neutral-200 dark:hover:bg-neutral-800">
              Shipments
            </a>
            <a
              routerLink="/drivers"
              routerLinkActive="border-b-3 border-b-neutral-800 dark:border-b-neutral-400 dark:bg-neutral-900 bg-neutral-100 text-neutral-900 dark:text-neutral-50 font-medium"
              class="px-4 py-2 text-sm text-muted-foreground transition-colors hover:bg-neutral-200 dark:hover:bg-neutral-800">
              Drivers
            </a>
            <a
              routerLink="/schedule"
              routerLinkActive="border-b-3 border-b-neutral-800 dark:border-b-neutral-400 dark:bg-neutral-900 bg-neutral-100 text-neutral-900 dark:text-neutral-50 font-medium"
              class="px-4 py-2 text-sm text-muted-foreground transition-colors hover:bg-neutral-200 dark:hover:bg-neutral-800">
              Schedule
            </a>
          </div>
        </nav>

        <div class="flex items-center gap-2">
          <app-theme-toggle />

          <button
            hlmBtn
            variant="ghost"
            size="icon"
            type="button"
            aria-label="Account menu"
            align="end"
            [hlmDropdownMenuTrigger]="accountMenu">
            <hlm-avatar size="sm">
              <span hlmAvatarFallback>{{ initials() }}</span>
            </hlm-avatar>
          </button>
        </div>

        <ng-template #accountMenu>
          <hlm-dropdown-menu class="min-w-48">
            @if (user(); as user) {
              <div hlmDropdownMenuLabel class="truncate">{{ user.email }}</div>
              <hlm-dropdown-menu-separator />
            }
            <a hlmDropdownMenuItem routerLink="/profile">
              <ng-icon name="lucideUser" />
              Profile
            </a>
            <button hlmDropdownMenuItem type="button" [disabled]="loggingOut()" (triggered)="onLogout()">
              @if (loggingOut()) {
                <hlm-spinner />
              } @else {
                <ng-icon name="lucideLogOut" />
              }
              Sign out
            </button>
          </hlm-dropdown-menu>
        </ng-template>
      </header>

      <div class="min-h-0 flex-1 overflow-y-auto">
        <router-outlet />
      </div>
    </div>
  `,
})
export class AppShell {
  private readonly authStore: AuthStoreType = inject(AuthStore);
  private readonly router: Router = inject(Router);

  protected readonly user: Signal<UserResponse | null> = this.authStore.user;
  protected readonly loggingOut: WritableSignal<boolean> = signal(false);

  protected readonly initials: Signal<string> = computed(() => {
    const email = this.user()?.email;
    return email ? email.charAt(0).toUpperCase() : '?';
  });

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
