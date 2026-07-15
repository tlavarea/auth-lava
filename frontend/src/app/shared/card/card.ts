import { Component, input, InputSignal } from '@angular/core';
import { HlmCardImports } from '@spartan-ng/helm/card';

@Component({
  selector: 'app-card',
  imports: [HlmCardImports],
  template: `
    <div hlmCard [class]="cardClass()">
      @if (title() || description()) {
        <div hlmCardHeader>
          @if (title()) {
            <h1 hlmCardTitle>{{ title() }}</h1>
          }
          @if (description()) {
            <p hlmCardDescription>{{ description() }}</p>
          }
          <div class="self-center" hlmCardAction>
            <ng-content select=".card-action" />
          </div>
        </div>
      }
      <div hlmCardContent [class]="contentClass()">
        <ng-content />
      </div>
    </div>
  `,
})
export class Card {
  readonly cardClass: InputSignal<string | string[] | undefined> = input<string | string[] | undefined>();
  readonly contentClass: InputSignal<string | string[] | undefined> = input<string | string[] | undefined>();
  readonly description: InputSignal<string | undefined> = input<string | undefined>();
  readonly title: InputSignal<string | undefined> = input<string | undefined>();
}
