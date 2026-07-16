import { Component, computed, inject, Signal, signal, WritableSignal } from '@angular/core';

import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmDialogImports } from '@spartan-ng/helm/dialog';
import { HlmSpinnerImports } from '@spartan-ng/helm/spinner';

import { ShipmentsStoreType } from '../shipments.store';
import { extractOfferResponseErrorMessage } from './extract-offer-response-error-message';

export type DeclineOfferDialogContext = {
  offerId: number;
  // See the identical comment in accept-offer.dialog.ts: ShipmentsStore is feature-scoped, not
  // root-provided, so it must be passed through the dialog context rather than injected here.
  store: ShipmentsStoreType;
};

@Component({
  imports: [HlmAlertImports, HlmButtonImports, HlmDialogImports, HlmSpinnerImports],
  template: `
    <hlm-dialog-header class="mb-8">
      <h3 hlmDialogTitle>Decline offer</h3>
      <p hlmDialogDescription>Are you sure you want to decline this offer?</p>
    </hlm-dialog-header>

    @if (submitError(); as submitError) {
      <div hlmAlert variant="destructive">
        <p hlmAlertDescription>{{ submitError }}</p>
      </div>
    }

    <hlm-dialog-footer>
      <button hlmBtn variant="ghost" type="button" hlmDialogClose [disabled]="submitting()">Cancel</button>
      <button hlmBtn variant="destructive" type="button" [disabled]="submitting()" (click)="onDecline()">
        @if (submitting()) {
          <hlm-spinner />
          Declining...
        } @else {
          Decline
        }
      </button>
    </hlm-dialog-footer>
  `,
})
export class DeclineOfferDialog {
  private readonly context: DeclineOfferDialogContext = injectBrnDialogContext<DeclineOfferDialogContext>();
  private readonly dialogRef: BrnDialogRef = inject(BrnDialogRef);
  private readonly store: ShipmentsStoreType = this.context.store;

  protected readonly submitError: WritableSignal<string | null> = signal(null);
  protected readonly submitting: Signal<boolean> = computed(() => this.store.respondStatus() === 'loading');

  protected async onDecline(): Promise<void> {
    this.submitError.set(null);
    try {
      await this.store.respondToOffer(this.context.offerId, {
        response: 'DECLINE',
        conveyancesAvailable: 0,
      });
      this.dialogRef.close();
    } catch (error) {
      this.submitError.set(extractOfferResponseErrorMessage(error));
    }
  }
}
