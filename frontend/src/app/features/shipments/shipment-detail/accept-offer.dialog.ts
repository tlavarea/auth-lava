import { Component, computed, inject, Signal, signal, WritableSignal } from '@angular/core';

import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmDialogImports } from '@spartan-ng/helm/dialog';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { ShipmentsStoreType } from '../shipments.store';
import { extractOfferResponseErrorMessage } from './extract-offer-response-error-message';

export type AcceptOfferDialogContext = {
  offerId: number;
  conveyancesAvailable: number;
  // ShipmentsStore is provided at ShipmentsPage, not root, so it isn't resolvable via DI from
  // inside a dialog rendered through the CDK overlay (HlmDialogService.open() opens without a
  // ViewContainerRef) — pass the already-injected instance through the dialog context instead.
  store: ShipmentsStoreType;
};

@Component({
  imports: [HlmAlertImports, HlmButtonImports, HlmDialogImports, HlmFieldImports, HlmInputImports, HlmSpinnerImports],
  template: `
    <hlm-dialog-header class="mb-8">
      <h3 hlmDialogTitle>Accept offer</h3>
    </hlm-dialog-header>

    <div class="my-8 flex flex-col gap-4">
      @if (submitError(); as submitError) {
        <div hlmAlert variant="destructive">
          <p hlmAlertDescription>{{ submitError }}</p>
        </div>
      }

      <div hlmField>
        <label hlmFieldLabel for="accept-conveyances-available">Conveyances Available</label>
        <input
          hlmInput
          id="accept-conveyances-available"
          type="number"
          min="0"
          [value]="conveyancesAvailable()"
          (input)="onConveyancesInput($event)" />
      </div>
    </div>

    <hlm-dialog-footer>
      <button hlmBtn variant="ghost" type="button" hlmDialogClose [disabled]="submitting()">Cancel</button>
      <button hlmBtn type="button" [disabled]="submitting()" (click)="onAccept()">
        @if (submitting()) {
          <hlm-spinner />
          Accepting...
        } @else {
          Accept
        }
      </button>
    </hlm-dialog-footer>
  `,
})
export class AcceptOfferDialog {
  private readonly context: AcceptOfferDialogContext = injectBrnDialogContext<AcceptOfferDialogContext>();
  private readonly dialogRef: BrnDialogRef = inject(BrnDialogRef);
  private readonly store: ShipmentsStoreType = this.context.store;

  protected readonly conveyancesAvailable: WritableSignal<number> = signal(this.context.conveyancesAvailable);
  protected readonly submitError: WritableSignal<string | null> = signal(null);
  protected readonly submitting: Signal<boolean> = computed(() => this.store.respondStatus() === 'loading');

  protected onConveyancesInput(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.conveyancesAvailable.set(Number.isNaN(value) ? 0 : value);
  }

  protected async onAccept(): Promise<void> {
    this.submitError.set(null);
    try {
      await this.store.respondToOffer(this.context.offerId, {
        response: 'ACCEPT',
        conveyancesAvailable: this.conveyancesAvailable(),
      });
      this.dialogRef.close();
    } catch (error) {
      this.submitError.set(extractOfferResponseErrorMessage(error));
    }
  }
}
