import { DatePipe } from '@angular/common';
import { Component, computed, effect, input, InputSignal, signal, Signal, viewChild } from '@angular/core';
import { GoogleMap, MapInfoWindow, MapMarker, MapPolyline } from '@angular/google-maps';

import { computeBoundsCenter } from '../../schedule/map-bounds';
import { TruckRoutePoint, TruckRouteStop, TruckSafetyEventEntry } from '../trucks.models';

const DEFAULT_ZOOM = 10;
// Same degenerate-bounds defense as schedule-manifest-map.ts's MAX_AUTO_FIT_ZOOM - a route with only one stop (or no
// movement at all) would otherwise make fitBounds snap to street level.
const MAX_AUTO_FIT_ZOOM = 14;
// Canceled if a new mouseover fires within this window - avoids a flash-closed-then-reopened flicker when moving the
// mouse between adjacent/overlapping markers (see onMarkerMouseout).
const INFO_WINDOW_CLOSE_DELAY_MS = 100;

// Neither a numeric google.maps.Symbol `path` nor a hand-drawn SVG path string (both single-shape) can express a
// bullseye's concentric rings or a filled circle plus an exclamation glyph - a data-URI SVG `google.maps.Icon` can.
// Built as plain string constants rather than `new google.maps.Size/Point(...)` for a `scaledSize`/`anchor` override
// - those, like `google.maps.SymbolPath.*` elsewhere in this codebase (see driver-location-map.ts), only exist once
// the async-loaded "maps" library finishes initializing, which isn't guaranteed at module-evaluation time. Baking
// width/height into the SVG itself instead sidesteps needing them at all.
function svgIconUrl(svg: string): string {
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg.trim())}`;
}

const STOP_MARKER_ICON: google.maps.Icon = {
  url: svgIconUrl(`
    <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 28 28">
      <circle cx="14" cy="14" r="12" fill="#ffffff" stroke="#1d4ed8" stroke-width="2"/>
      <circle cx="14" cy="14" r="8" fill="none" stroke="#1d4ed8" stroke-width="2"/>
      <circle cx="14" cy="14" r="3.5" fill="#1d4ed8"/>
    </svg>
  `),
};

const SAFETY_EVENT_MARKER_ICON: google.maps.Icon = {
  url: svgIconUrl(`
    <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 28 28">
      <circle cx="14" cy="14" r="12" fill="#facc15" stroke="#000000" stroke-width="1.5"/>
      <rect x="12.5" y="6" width="3" height="10" rx="1.5" fill="#000000"/>
      <circle cx="14" cy="20" r="1.8" fill="#000000"/>
    </svg>
  `),
};

// A green play-button triangle at the truck's current (most recent) location - same shape/color as
// schedule-manifest-map.ts's ORIGIN_MARKER_ICON. A plain numeric-literal `path`/hand-drawn SVG path string, not
// `google.maps.SymbolPath.*` - see driver-location-map.ts's comment on why that enum isn't safe to read at
// module-evaluation time.
const CURRENT_LOCATION_MARKER_ICON: google.maps.Symbol = {
  path: 'M -5,-7 L 7,0 L -5,7 Z',
  scale: 1.8,
  fillColor: '#16a34a',
  fillOpacity: 1,
  strokeColor: '#ffffff',
  strokeWeight: 2,
};

type HoveredMarker = { type: 'stop'; stop: TruckRouteStop } | { type: 'safetyEvent'; event: TruckSafetyEventEntry };

// Small, bespoke local formatter (not a shared date-utility, matching schedule-chart.ts's formatDateRange/formatMdy
// precedent) - "3h 12m" / "45m".
function formatStoppedDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (hours === 0) {
    return `${remainingMinutes}m`;
  }
  return remainingMinutes === 0 ? `${hours}h` : `${hours}h ${remainingMinutes}m`;
}

/**
 * Renders a truck's driven route for the day (a polyline through every raw GPS point), where it stopped for at least
 * 5 minutes (bullseye markers), any Samsara-flagged safety events (yellow exclamation markers), and its current
 * (most recent) location at the end of the route (a green play-button triangle) - for the truck detail page,
 * directly below the Diagnostics section. A self-contained sibling to DriverLocationMap/
 * ScheduleManifestMap rather than a reuse of either (this codebase's established convention: each map component is
 * purpose-built for its own data shape and lifecycle). Unlike ScheduleManifestMap's encoded route polyline, `points`
 * here are already plain, time-ordered lat/lng samples, so no decode-polyline.ts is needed.
 *
 * Hover tooltips use a single shared `<map-info-window>` (new to this codebase's map components) rather than one per
 * marker - opened/closed imperatively via a `hoveredMarker` signal on each marker's (mapMouseover)/(mapMouseout), with
 * `disableAutoPan` so a mere hover doesn't pan the map, and a short debounce on close (see
 * INFO_WINDOW_CLOSE_DELAY_MS) so moving between adjacent markers doesn't flicker.
 */
@Component({
  selector: 'app-truck-route-map',
  imports: [DatePipe, GoogleMap, MapInfoWindow, MapMarker, MapPolyline],
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  template: `
    <google-map
      class="block min-h-0 flex-1 overflow-hidden rounded-md"
      height="100%"
      width="100%"
      role="img"
      aria-label="Truck route map"
      [center]="center()"
      [zoom]="DEFAULT_ZOOM"
      [options]="mapOptions"
      (idle)="onMapIdle()"
      (mapInitialized)="googleMap.set($event)">
      @if (path().length > 0) {
        <map-polyline [path]="path()" [options]="polylineOptions" />
      }
      @if (currentLocation(); as position) {
        <map-marker title="Current location" [position]="position" [icon]="currentLocationMarkerIcon" />
      }
      @for (stop of stops(); track stop.arrivalTime) {
        <map-marker
          #marker="mapMarker"
          title="Stop"
          [position]="{ lat: stop.latitude, lng: stop.longitude }"
          [icon]="stopMarkerIcon"
          (mapMouseover)="onStopMouseover(marker, stop)"
          (mapMouseout)="onMarkerMouseout()" />
      }
      @for (event of safetyEvents(); track event.id) {
        <map-marker
          #marker="mapMarker"
          title="Safety event"
          [position]="{ lat: event.latitude, lng: event.longitude }"
          [icon]="safetyEventMarkerIcon"
          (mapMouseover)="onSafetyEventMouseover(marker, event)"
          (mapMouseout)="onMarkerMouseout()" />
      }
      <map-info-window [options]="infoWindowOptions">
        @if (hoveredMarker(); as hovered) {
          @if (hovered.type === 'stop') {
            <div [class]="infoWindowContentClass">
              <span class="font-semibold">{{ driverName() ?? 'Unknown driver' }}</span>
              <span>{{ hovered.stop.formattedLocation ?? 'Unknown location' }}</span>
              <span>
                {{ hovered.stop.arrivalTime | date: 'h:mm a' }} – {{ hovered.stop.departureTime | date: 'h:mm a' }}
              </span>
              <span>Stopped {{ formatStoppedDuration(hovered.stop.stoppedMinutes) }}</span>
            </div>
          } @else {
            <div [class]="infoWindowContentClass">
              <span class="font-semibold">{{ hovered.event.behaviorLabels.join(', ') }}</span>
              @if (hovered.event.address) {
                <span>{{ hovered.event.address }}</span>
              }
              @if (hovered.event.mediaUrl; as mediaUrl) {
                <a class="text-primary underline" target="_blank" rel="noopener noreferrer" [href]="mediaUrl">
                  View media
                </a>
              }
            </div>
          }
        }
      </map-info-window>
    </google-map>
  `,
})
export class TruckRouteMap {
  readonly points: InputSignal<TruckRoutePoint[]> = input.required<TruckRoutePoint[]>();
  readonly stops: InputSignal<TruckRouteStop[]> = input.required<TruckRouteStop[]>();
  readonly safetyEvents: InputSignal<TruckSafetyEventEntry[]> = input.required<TruckSafetyEventEntry[]>();
  readonly driverName: InputSignal<string | null> = input.required<string | null>();

  private readonly infoWindow = viewChild.required(MapInfoWindow);

  // Set from the template's (mapInitialized) binding - see schedule-manifest-map.ts's identical field for why this
  // can't just be a viewChild(GoogleMap) read instead.
  protected readonly googleMap = signal<google.maps.Map | null>(null);

  // Tracks which `points` array the initial fitBounds already ran for (by reference, not a truckId input this
  // component doesn't have) - points() gets a brand-new array only when the parent's loadTruckMapData resolves,
  // which happens once per truck detail visit, so this reliably fires the auto-fit exactly once per truck without
  // re-fitting on incidental re-renders (e.g. the hover tooltip's own state changing).
  private fitBoundsForPoints: TruckRoutePoint[] | null = null;
  private pendingZoomClampCheck = false;
  private closeTimeoutId: ReturnType<typeof setTimeout> | null = null;

  protected readonly DEFAULT_ZOOM = DEFAULT_ZOOM;
  protected readonly stopMarkerIcon = STOP_MARKER_ICON;
  protected readonly safetyEventMarkerIcon = SAFETY_EVENT_MARKER_ICON;
  protected readonly currentLocationMarkerIcon = CURRENT_LOCATION_MARKER_ICON;
  // No zoomControlOptions.position override - same async-loaded-enum gotcha as driver-location-map.ts's mapOptions.
  protected readonly mapOptions: google.maps.MapOptions = { zoomControl: true };
  protected readonly polylineOptions: google.maps.PolylineOptions = { strokeColor: '#2563eb', strokeWeight: 4 };
  // headerDisabled removes the whole header row, which is the only thing that would otherwise render a close
  // button - there's no click opportunity to use one anyway, since this window only ever opens/closes on marker
  // (mapMouseover)/(mapMouseout) (see onMarkerMouseout's debounce), never a user click.
  protected readonly infoWindowOptions: google.maps.InfoWindowOptions = { disableAutoPan: true, headerDisabled: true };
  // Matches libs/ui/hover-card/src/lib/hlm-hover-card-content.ts's static box classes (background/text color,
  // width, rounded corners, padding, shadow, ring) so this tooltip reads as the same "card" as hlm-hover-card
  // elsewhere in the app - minus that component's data-state/data-side-driven open/close animation classes, which
  // don't apply here since MapInfoWindow's own show/hide isn't wired through the brain hover-card's state machine.
  // Google's remaining default InfoWindow chrome (white background, drop shadow, pointer arrow) is stripped via the
  // global .gm-style-iw-* overrides in styles.css so this is the only visible surface, not a card-in-a-card.
  protected readonly infoWindowContentClass =
    'flex w-64 flex-col gap-1 rounded-lg bg-popover p-4 text-sm text-popover-foreground shadow-md ' +
    'ring-1 ring-foreground/10';

  protected readonly hoveredMarker = signal<HoveredMarker | null>(null);
  protected readonly formatStoppedDuration = formatStoppedDuration;

  protected readonly path: Signal<google.maps.LatLngLiteral[]> = computed(() =>
    this.points().map((point) => ({ lat: point.latitude, lng: point.longitude }))
  );

  // points() is already time-ordered ascending (see the backend's TruckRouteHistoryService), so the truck's current
  // location is simply the most recent sample - the last point of the day's path, i.e. the end of the route.
  protected readonly currentLocation: Signal<google.maps.LatLngLiteral | null> = computed(() => {
    const path = this.path();
    return path.length > 0 ? path[path.length - 1] : null;
  });

  private readonly allPositions: Signal<google.maps.LatLngLiteral[]> = computed(() => [
    ...this.path(),
    ...this.stops().map((stop) => ({ lat: stop.latitude, lng: stop.longitude })),
    ...this.safetyEvents().map((event) => ({ lat: event.latitude, lng: event.longitude })),
  ]);

  // google-map always needs a center, even before fitBounds can run - see schedule-manifest-map.ts's identical
  // comment for why this uses computeBoundsCenter rather than a single fallback position.
  protected readonly center: Signal<google.maps.LatLngLiteral> = computed(
    () => computeBoundsCenter(this.allPositions()) ?? { lat: 0, lng: 0 }
  );

  constructor() {
    effect(() => {
      const map = this.googleMap();
      const points = this.points();
      const positions = this.allPositions();
      if (!map || positions.length === 0 || this.fitBoundsForPoints === points) {
        return;
      }
      const bounds = new google.maps.LatLngBounds();
      positions.forEach((position) => bounds.extend(position));
      this.pendingZoomClampCheck = true;
      map.fitBounds(bounds);
      this.fitBoundsForPoints = points;
    });
  }

  // Same zoom-clamp-after-idle pattern as schedule-manifest-map.ts's onMapIdle - pendingZoomClampCheck means this
  // only actually clamps immediately following our own fitBounds call above, not a dispatcher's manual zoom/pan.
  protected onMapIdle(): void {
    if (!this.pendingZoomClampCheck) {
      return;
    }
    this.pendingZoomClampCheck = false;
    const map = this.googleMap();
    const zoom = map?.getZoom();
    if (map && zoom !== undefined && zoom > MAX_AUTO_FIT_ZOOM) {
      map.setZoom(MAX_AUTO_FIT_ZOOM);
    }
  }

  protected onStopMouseover(marker: MapMarker, stop: TruckRouteStop): void {
    this.openInfoWindow(marker, { type: 'stop', stop });
  }

  protected onSafetyEventMouseover(marker: MapMarker, event: TruckSafetyEventEntry): void {
    this.openInfoWindow(marker, { type: 'safetyEvent', event });
  }

  protected onMarkerMouseout(): void {
    this.cancelPendingClose();
    this.closeTimeoutId = setTimeout(() => {
      this.infoWindow().close();
      this.hoveredMarker.set(null);
      this.closeTimeoutId = null;
    }, INFO_WINDOW_CLOSE_DELAY_MS);
  }

  private openInfoWindow(marker: MapMarker, hovered: HoveredMarker): void {
    this.cancelPendingClose();
    this.hoveredMarker.set(hovered);
    this.infoWindow().open(marker);
  }

  private cancelPendingClose(): void {
    if (this.closeTimeoutId !== null) {
      clearTimeout(this.closeTimeoutId);
      this.closeTimeoutId = null;
    }
  }
}
