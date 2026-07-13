import { Component, inject, Signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMoon, lucideSun } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';

import { Theme, ThemeStore, ThemeStoreType } from '@core/theme/theme.store';

@Component({
  selector: 'app-theme-toggle',
  imports: [NgIcon, HlmButtonImports],
  viewProviders: [provideIcons({ lucideMoon, lucideSun })],
  template: `
    <button
      hlmBtn
      variant="ghost"
      size="icon"
      type="button"
      [attr.aria-label]="theme() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'"
      (click)="themeStore.toggle()">
      <ng-icon [name]="theme() === 'dark' ? 'lucideSun' : 'lucideMoon'" />
    </button>
  `,
})
export class ThemeToggle {
  protected readonly themeStore: ThemeStoreType = inject(ThemeStore);
  protected readonly theme: Signal<Theme> = this.themeStore.theme;
}
