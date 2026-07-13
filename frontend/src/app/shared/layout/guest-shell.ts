import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ThemeToggle } from '@shared/theme-toggle/theme-toggle';

@Component({
  selector: 'app-guest-shell',
  imports: [RouterOutlet, ThemeToggle],
  template: `
    <div class="fixed top-4 right-4 z-10">
      <app-theme-toggle />
    </div>
    <router-outlet />
  `,
})
export class GuestShell {}
