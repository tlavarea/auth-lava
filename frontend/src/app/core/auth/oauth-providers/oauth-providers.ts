import { Component } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { faBrandGithub } from '@ng-icons/font-awesome/brands';
import { matfGoogleColored } from '@ng-icons/material-file-icons/colored';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmSeparatorImports } from '@spartan-ng/helm/separator';

@Component({
  selector: 'app-oauth-providers',
  imports: [HlmButtonImports, HlmSeparatorImports, NgIcon],
  viewProviders: [provideIcons({ faBrandGithub, matfGoogleColored })],
  template: `
    <div class="mb-4 flex max-w-sm flex-col gap-8 text-sm">
      <div class="flex flex-row gap-2">
        <button class="flex-1" hlmBtn variant="outline" type="button" (click)="signInWithGoogle()">
          <ng-icon name="matfGoogleColored" />
          Google
        </button>
        <button class="flex-1" hlmBtn variant="outline" type="button" (click)="signInWithGithub()">
          <ng-icon name="faBrandGithub" />
          GitHub
        </button>
      </div>
      <hlm-separator>
        <div
          class="relative -top-3.75 m-auto flex h-7.5 w-12.5 items-center justify-center bg-white dark:bg-(--card) dark:text-(--card-foreground)">
          or
        </div>
      </hlm-separator>
    </div>
  `,
})
export class OauthProviders {
  protected signInWithGoogle(): void {
    window.location.href = '/oauth2/authorization/google';
  }

  protected signInWithGithub(): void {
    window.location.href = '/oauth2/authorization/github';
  }
}
