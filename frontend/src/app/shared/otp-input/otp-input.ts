import { Component, computed, input, InputSignal, output, OutputEmitterRef, Signal } from '@angular/core';
import { BrnInputOtp, BrnInputOtpImports } from '@spartan-ng/brain/input-otp';
import { HlmInputOtpImports } from '@spartan-ng/helm/input-otp';

@Component({
  selector: 'app-otp-input',
  imports: [BrnInputOtp, BrnInputOtpImports, HlmInputOtpImports],
  template: `
    <brn-input-otp
      hlmInputOtp
      [disabled]="disabled()"
      [maxLength]="maxLength()"
      [inputId]="inputId()"
      (valueChange)="valueChange.emit($event)"
      (completed)="completed.emit()">
      @for (slot of slots(); track $index) {
        <hlm-input-otp-group>
          <hlm-input-otp-slot [index]="slot" />
        </hlm-input-otp-group>
      }
    </brn-input-otp>
  `,
  styles: ``,
})
export class OtpInput {
  protected readonly slots: Signal<number[]> = computed((): number[] => {
    return Array.from({ length: this.maxLength() }, (_, i) => i);
  });

  readonly disabled: InputSignal<boolean> = input(false);
  readonly inputId: InputSignal<string> = input.required<string>();
  readonly maxLength: InputSignal<number> = input.required<number>();
  readonly completed: OutputEmitterRef<void> = output<void>();
  readonly valueChange: OutputEmitterRef<string> = output<string>();
}
