import { DatePipe } from '@angular/common';
import { Component, computed, input, InputSignal, Signal } from '@angular/core';

import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMapPin } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';

import { ManifestRoute, ManifestStartingPosition, ManifestStop } from './schedule.models';

type StopStatus = 'Completed' | 'Arrived' | 'En Route';
type StopStatusVariant = 'success' | 'warning' | 'info';

// Vektor has no separate status field per stop (see ManifestStop's javadoc-mirroring comment in schedule.models.ts) -
// this infers the same three states its own UI shows from the actual arrival/check-in/check-out timestamps a stop
// already carries.
function stopStatus(stop: ManifestStop): StopStatus {
  if (stop.checkedOutAt !== null) {
    return 'Completed';
  }
  if (stop.arrivedAt !== null) {
    return 'Arrived';
  }
  return 'En Route';
}

function stopStatusVariant(status: StopStatus): StopStatusVariant {
  switch (status) {
    case 'Completed':
      return 'success';
    case 'Arrived':
      return 'warning';
    case 'En Route':
      return 'info';
  }
}

// The "est. X mi // actual Y mi // ODO: Z mi" line Vektor shows between two consecutive stops (or the starting
// position and the first stop) - built from whichever of the three pieces are actually present, since the last stop
// has no outbound leg and reports none of them. Returns null (render nothing) rather than an empty string when none
// of the three are available, so a missing leg doesn't leave a stray separator line.
function legSummary(
  estimatedMiles: number | null,
  actualMiles: number | null,
  odometerMiles: number | null
): string | null {
  const parts: string[] = [];
  if (estimatedMiles !== null) {
    parts.push(`est. ${estimatedMiles} mi`);
  }
  if (actualMiles !== null) {
    parts.push(`actual ${actualMiles} mi`);
  }
  if (odometerMiles !== null) {
    parts.push(`ODO: ${odometerMiles} mi`);
  }
  return parts.length === 0 ? null : parts.join(' // ');
}

/**
 * The left-hand pane of the Schedule page's manifest panel (30% width, map at 70% alongside it in
 * ScheduleManifestMap) - an ordered, stop-by-stop breakdown of a manifest's route modeled on Vektor's own manifest
 * detail view: the truck's starting position (if Vektor reported one), then every pickup/dropoff in sequence, each
 * with its status, appointment window, actual arrival/check-in/check-out times, address, references, contact, and
 * notes, with the estimated/actual mileage and odometer reading for the leg to the next stop shown between rows.
 * Deliberately independent of ScheduleManifestMap's own marker/selection state (no click-to-highlight in either
 * direction) - both simply render the same ManifestRoute their shared parent (ScheduleStore) already fetched once.
 */
@Component({
  selector: 'app-schedule-manifest-detail',
  imports: [DatePipe, NgIcon, HlmBadgeImports],
  viewProviders: [provideIcons({ lucideMapPin })],
  host: { class: 'block overflow-y-auto' },
  template: `
    <div class="divide-y">
      @if (startingPosition(); as position) {
        <div class="flex items-start gap-2 py-3 first:pt-0">
          <ng-icon name="lucideMapPin" class="mt-0.5 shrink-0 text-muted-foreground" />
          <div class="min-w-0 flex-1">
            <p class="text-xs font-medium text-muted-foreground">Start</p>
            @if (position.address) {
              <p class="truncate text-sm">{{ position.address }}</p>
            }
            @if (position.note) {
              <p class="truncate text-xs text-muted-foreground">{{ position.note }}</p>
            }
          </div>
        </div>
        @if (startingPositionLeg(); as leg) {
          <p class="py-1.5 text-center text-[11px] text-muted-foreground">{{ leg }}</p>
        }
      }

      @for (stop of stops(); track stop.sequenceNumber) {
        <div class="space-y-1.5 py-3 first:pt-0">
          <div class="flex items-start justify-between gap-2">
            <div class="flex min-w-0 items-start gap-2">
              <span
                class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-bold text-white"
                [class]="stop.stopType === 'PICKUP' ? 'bg-green-600' : 'bg-red-600'">
                {{ stop.sequenceNumber }}
              </span>
              <div class="min-w-0">
                <p class="text-sm font-medium">
                  {{ stop.stopType === 'PICKUP' ? 'Pick Up' : 'Drop Off' }}
                  @if (stop.appointmentWindowStart) {
                    <span class="font-normal text-muted-foreground">
                      // {{ stop.appointmentWindowStart | date: 'MMM d, h:mm' }}
                      @if (stop.appointmentWindowEnd) {
                        - {{ stop.appointmentWindowEnd | date: 'h:mm' }}
                      }
                      {{ stop.timezoneAbbreviation }}
                    </span>
                  }
                </p>
              </div>
            </div>
            <span hlmBadge class="shrink-0" [variant]="stopStatusVariant(stopStatus(stop))">{{
              stopStatus(stop)
            }}</span>
          </div>

          @if (stop.arrivedAt || stop.checkedInAt || stop.checkedOutAt) {
            <div class="ml-7 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-muted-foreground">
              @if (stop.arrivedAt) {
                <span>Arrived {{ stop.arrivedAt | date: 'short' }}</span>
              }
              @if (stop.checkedInAt) {
                <span>Check In {{ stop.checkedInAt | date: 'short' }}</span>
              }
              @if (stop.checkedOutAt) {
                <span>Check Out {{ stop.checkedOutAt | date: 'short' }}</span>
              }
            </div>
          }

          <div class="ml-7 min-w-0">
            @if (stop.siteName) {
              <p class="truncate text-sm font-medium">{{ stop.siteName }}</p>
            }
            @if (stop.address) {
              <p class="truncate text-sm text-muted-foreground">{{ stop.address }}</p>
            }
            <div class="flex flex-wrap gap-x-3 text-xs text-muted-foreground">
              @if (stop.referenceNumbers) {
                <span>Ref # {{ stop.referenceNumbers }}</span>
              }
              @if (stop.contactPhone) {
                <span>Contact: {{ stop.contactPhone }}</span>
              }
            </div>
            @if (stop.notes) {
              <p class="text-xs whitespace-pre-line text-muted-foreground italic">{{ stop.notes }}</p>
            }
          </div>
        </div>

        @if (legSummaryFor(stop); as leg) {
          <p class="py-1.5 text-center text-[11px] text-muted-foreground">{{ leg }}</p>
        }
      }
    </div>
  `,
})
export class ScheduleManifestDetail {
  readonly route: InputSignal<ManifestRoute | null> = input.required<ManifestRoute | null>();

  protected readonly stopStatus = stopStatus;
  protected readonly stopStatusVariant = stopStatusVariant;

  protected readonly startingPosition: Signal<ManifestStartingPosition | null> = computed(
    () => this.route()?.startingPosition ?? null
  );
  protected readonly stops: Signal<ManifestStop[]> = computed(() => this.route()?.stops ?? []);

  protected readonly startingPositionLeg: Signal<string | null> = computed(() => {
    const position = this.startingPosition();
    return position === null
      ? null
      : legSummary(position.estimatedMilesToNext, position.actualMilesToNext, position.odometerMiles);
  });

  protected legSummaryFor(stop: ManifestStop): string | null {
    return legSummary(stop.estimatedMilesToNext, stop.actualMilesToNext, stop.odometerMiles);
  }
}
