import { Component, computed, input, InputSignal, Signal } from '@angular/core';

import { formatDurationMs } from '../format-duration';

const RADIUS = 28;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

// One HOS clock ring (Break/Drive/Shift/Cycle) - a hand-built SVG stroke-dasharray ring, since no ring/gauge/chart
// primitive exists in libs/ui (only the linear HlmProgress). aria-label states the remaining time as text so the
// status isn't conveyed by ring color/fill alone (WCAG AA). Blue is a deliberate content-specific color choice here
// (not a theme-semantic token like --primary), matching the rest of this HOS accordion's restyle.
@Component({
  selector: 'app-hos-clock-ring',
  host: { class: 'flex flex-col items-center gap-1' },
  template: `
    <div role="img" [attr.aria-label]="ariaLabel()">
      <svg viewBox="0 0 72 72" class="h-20 w-20 -rotate-90" aria-hidden="true">
        <circle cx="36" cy="36" fill="none" stroke-width="7" class="stroke-muted" [attr.r]="RADIUS" />
        <circle
          cx="36"
          cy="36"
          fill="none"
          stroke-width="7"
          stroke-linecap="round"
          class="stroke-blue-600 dark:stroke-blue-400"
          [attr.r]="RADIUS"
          [attr.stroke-dasharray]="CIRCUMFERENCE"
          [attr.stroke-dashoffset]="dashOffset()" />
      </svg>
    </div>
    <span class="text-lg font-semibold">{{ formattedRemaining() }}</span>
    <span class="text-sm text-muted-foreground">{{ label() }}</span>
  `,
})
export class HosClockRing {
  readonly label: InputSignal<string> = input.required<string>();
  readonly remainingMs: InputSignal<number | null> = input<number | null>(null);
  readonly totalMs: InputSignal<number> = input.required<number>();

  protected readonly RADIUS = RADIUS;
  protected readonly CIRCUMFERENCE = CIRCUMFERENCE;

  protected readonly formattedRemaining: Signal<string> = computed(() => formatDurationMs(this.remainingMs()));

  protected readonly percentRemaining: Signal<number> = computed(() => {
    const remaining = this.remainingMs();
    return remaining === null ? 0 : Math.min(1, Math.max(0, remaining / this.totalMs()));
  });

  protected readonly dashOffset: Signal<number> = computed(() => CIRCUMFERENCE * (1 - this.percentRemaining()));

  protected readonly ariaLabel: Signal<string> = computed(
    () => `${this.label()}: ${this.formattedRemaining()} remaining`
  );
}
