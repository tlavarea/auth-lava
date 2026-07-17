import { DatePipe } from '@angular/common';
import { Component, input, InputSignal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideMapPin } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';

import { driverDutyStatusLabel, driverDutyStatusVariant } from '../driver-status';
import { DriverActivityEntry } from '../drivers.models';

// The "Today • date, location, list" body of the Activity panel - deliberately doesn't own the "Activity" title
// itself, since that plays a different structural role in each of driver-detail.page.ts's two renderings of this
// component (a plain heading on the desktop floating panel vs. an accordion trigger's label on mobile).
@Component({
  selector: 'app-driver-activity-feed',
  imports: [HlmBadgeImports, NgIcon, DatePipe],
  providers: [provideIcons({ lucideMapPin })],
  // flex-col + the <ul>'s own flex-1/overflow-y-auto below keeps "Today"/location pinned while only the entries
  // scroll - but only once an ancestor actually bounds this host's height (the desktop floating panel in
  // driver-detail.page.ts). Inside the mobile accordion (no bounded ancestor), this is inert and the list simply
  // renders at full natural height, unchanged from before.
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  template: `
    <!-- "Today" is a constant label, not a relative-date calculation - entries() is always scoped to today
         (see DriversStore.loadDriverActivity), so it's never inaccurate. -->
    <p class="mb-2 shrink-0 px-4 text-sm text-muted-foreground">Today • {{ asOf() | date: 'MMM d, y h:mm a' }}</p>
    @if (currentLocation(); as location) {
      <p class="mb-2 flex shrink-0 items-center gap-1 px-4 text-sm">
        <ng-icon name="lucideMapPin" class="shrink-0 text-muted-foreground" />
        {{ location }}
      </p>
    }
    <ul class="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto px-4">
      @for (entry of entries(); track entry.startTime + (entry.dutyStatus ?? '')) {
        <li class="flex h-12 shrink-0 items-center justify-between gap-4 border-b text-sm">
          <div class="flex flex-row gap-1">
            <span hlmBadge class="w-fit" [variant]="driverDutyStatusVariant(entry.dutyStatus)">
              {{ driverDutyStatusLabel(entry.dutyStatus) }}
            </span>
            @if (entry.remark) {
              <span class="text-muted-foreground">{{ entry.remark }}</span>
            }
          </div>
          <span class="shrink-0 text-muted-foreground">{{ entry.startTime | date: 'h:mm a' }}</span>
        </li>
      } @empty {
        <li class="text-sm text-muted-foreground">No activity today.</li>
      }
    </ul>
  `,
})
export class DriverActivityFeed {
  readonly entries: InputSignal<DriverActivityEntry[]> = input.required<DriverActivityEntry[]>();
  // An ISO timestamp for the "Today • ..." line - typically the driver's last-known location time.
  readonly asOf: InputSignal<string | null> = input<string | null>(null);
  readonly currentLocation: InputSignal<string | null> = input<string | null>(null);

  protected readonly driverDutyStatusVariant = driverDutyStatusVariant;
  protected readonly driverDutyStatusLabel = driverDutyStatusLabel;
}
