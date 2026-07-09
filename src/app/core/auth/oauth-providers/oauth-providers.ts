import { Component } from '@angular/core';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmFieldImports } from '@spartan-ng/helm/field';

@Component({
  selector: 'app-oauth-providers',
  imports: [HlmButton, HlmFieldImports],
  template: `
    <hlm-field-separator>OR</hlm-field-separator>

    <div class="flex flex-col gap-2">
      <button hlmBtn variant="outline" type="button" (click)="signInWithGoogle()">Continue with Google</button>
      <button hlmBtn variant="outline" type="button" (click)="signInWithGithub()">Continue with GitHub</button>
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
