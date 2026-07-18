import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  InputSignal,
  signal,
  Signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { GoogleMap, MapMarker, MapPolyline } from '@angular/google-maps';
import { firstValueFrom, timer } from 'rxjs';

import { DriversApi } from '@features/drivers/drivers-api';
import { DriverLiveLocationResponse } from '@features/drivers/drivers.models';
import { decodePolyline } from './decode-polyline';
import { ScheduleApi } from './schedule-api';
import { ManifestRoute, ManifestSegment } from './schedule.models';

const DEFAULT_ZOOM = 6;
// Matches driver-detail.page.ts's cadence for the same live-location call.
const LIVE_LOCATION_POLL_INTERVAL_MS = 15_000;
const ARROW_SCALE = 6;
const PIN_SCALE = 8;

// path/rotation use numeric SymbolPath literals rather than google.maps.SymbolPath.* - see driver-location-map.ts's
// comment on why: that enum only exists once the async-loaded "maps" library finishes initializing, which isn't
// guaranteed by the time these computeds first run. 0 is SymbolPath.CIRCLE, 1 is FORWARD_CLOSED_ARROW.
const ORIGIN_MARKER_ICON: google.maps.Symbol = {
  path: 0,
  scale: PIN_SCALE,
  fillColor: '#16a34a',
  fillOpacity: 1,
  strokeColor: '#ffffff',
  strokeWeight: 2,
};
const DESTINATION_MARKER_ICON: google.maps.Symbol = {
  path: 0,
  scale: PIN_SCALE,
  fillColor: '#dc2626',
  fillOpacity: 1,
  strokeColor: '#ffffff',
  strokeWeight: 2,
};

/**
 * Renders a manifest's route (origin pin, destination pin, and the driving path between them) plus the driver's
 * current position, for the map panel that opens below the Schedule grid when a manifest segment is clicked. A
 * self-contained sibling to DriverLocationMap rather than a reuse of it - that component is purpose-built for a
 * single live marker with dead-reckoning animation in a layout suited to driver-detail's absolute-fill container.
 * This component ports its marker/heading gotchas (see the icon constants above) but skips the lerp/dead-reckoning
 * animation, since this panel doesn't need per-200ms smoothness - the driver marker just moves to its latest polled
 * position on each tick.
 */
@Component({
  selector: 'app-schedule-manifest-map',
  imports: [GoogleMap, MapMarker, MapPolyline],
  host: { class: 'flex min-h-0 flex-1 flex-col' },
  template: `
    <google-map
      class="block min-h-0 flex-1 overflow-hidden rounded-md"
      height="100%"
      width="100%"
      role="img"
      [center]="center()"
      [zoom]="DEFAULT_ZOOM"
      [options]="mapOptions"
      [attr.aria-label]="ariaLabel()">
      @if (decodedPath(); as path) {
        <map-polyline [path]="path" [options]="polylineOptions" />
      }
      @if (originPosition(); as position) {
        <map-marker title="Origin" [position]="position" [icon]="originMarkerIcon" />
      }
      @if (destinationPosition(); as position) {
        <map-marker title="Destination" [position]="position" [icon]="destinationMarkerIcon" />
      }
      @if (driverPosition(); as position) {
        @if (driverMarkerIcon(); as icon) {
          <map-marker title="Driver" [position]="position" [icon]="icon" />
        } @else {
          <map-marker title="Driver" [position]="position" />
        }
      }
    </google-map>
  `,
})
export class ScheduleManifestMap {
  readonly driverId: InputSignal<string> = input.required<string>();
  readonly manifest: InputSignal<ManifestSegment> = input.required<ManifestSegment>();

  private readonly scheduleApi: ScheduleApi = inject(ScheduleApi);
  private readonly driversApi: DriversApi = inject(DriversApi);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly mapRef = viewChild(GoogleMap);

  protected readonly DEFAULT_ZOOM = DEFAULT_ZOOM;
  protected readonly originMarkerIcon = ORIGIN_MARKER_ICON;
  protected readonly destinationMarkerIcon = DESTINATION_MARKER_ICON;
  // No zoomControlOptions.position override - same async-loaded-enum gotcha as driver-location-map.ts's mapOptions.
  protected readonly mapOptions: google.maps.MapOptions = { zoomControl: true };
  protected readonly polylineOptions: google.maps.PolylineOptions = { strokeColor: '#2563eb', strokeWeight: 4 };

  private readonly route = signal<ManifestRoute | null>(null);
  private readonly liveLocation = signal<DriverLiveLocationResponse | null>(null);

  protected readonly decodedPath: Signal<google.maps.LatLngLiteral[] | null> = computed(() => {
    const route = this.route();
    return route === null ? null : decodePolyline(route.encodedPolyline);
  });

  protected readonly originPosition: Signal<google.maps.LatLngLiteral | null> = computed(() => {
    const route = this.route();
    return route === null ? null : { lat: route.originLatitude, lng: route.originLongitude };
  });

  protected readonly destinationPosition: Signal<google.maps.LatLngLiteral | null> = computed(() => {
    const route = this.route();
    return route === null ? null : { lat: route.destinationLatitude, lng: route.destinationLongitude };
  });

  protected readonly driverPosition: Signal<google.maps.LatLngLiteral | null> = computed(() => {
    const location = this.liveLocation();
    if (location === null || location.latitude === null || location.longitude === null) {
      return null;
    }
    return { lat: location.latitude, lng: location.longitude };
  });

  protected readonly driverMarkerIcon: Signal<google.maps.Symbol | null> = computed(() => {
    const heading = this.liveLocation()?.heading;
    if (heading === null || heading === undefined) {
      return null;
    }
    return {
      path: 1,
      rotation: heading,
      scale: ARROW_SCALE,
      strokeColor: '#ffffff',
      strokeWeight: 1,
      fillColor: '#2563eb',
      fillOpacity: 1,
    };
  });

  // google-map always needs a center, even before a route/location has loaded - falls back through whichever
  // position is available first, then (0, 0) until any of them are.
  protected readonly center: Signal<google.maps.LatLngLiteral> = computed(
    () => this.originPosition() ?? this.destinationPosition() ?? this.driverPosition() ?? { lat: 0, lng: 0 }
  );

  protected readonly ariaLabel: Signal<string> = computed(() => {
    const manifest = this.manifest();
    const from = manifest.origin ?? 'unknown origin';
    const to = manifest.destination ?? 'unknown destination';
    return `Route map from ${from} to ${to}`;
  });

  constructor() {
    effect(() => void this.loadRoute(this.manifest().manifestNumber));
    effect(() => void this.pollLiveLocation(this.driverId()));

    timer(LIVE_LOCATION_POLL_INTERVAL_MS, LIVE_LOCATION_POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.pollLiveLocation(this.driverId()));

    // Keeps the whole route (and the driver's position, if known) in view rather than relying on a fixed zoom, which
    // could easily clip either end of a long-haul route.
    effect(() => {
      const map = this.mapRef()?.googleMap;
      const origin = this.originPosition();
      const destination = this.destinationPosition();
      if (!map || origin === null || destination === null) {
        return;
      }
      const bounds = new google.maps.LatLngBounds();
      bounds.extend(origin);
      bounds.extend(destination);
      const driver = this.driverPosition();
      if (driver !== null) {
        bounds.extend(driver);
      }
      map.fitBounds(bounds);
    });
  }

  private async loadRoute(manifestNumber: number): Promise<void> {
    try {
      const route = await firstValueFrom(this.scheduleApi.route(manifestNumber));
      this.route.set(route);
    } catch {
      this.route.set(null);
    }
  }

  private async pollLiveLocation(driverId: string): Promise<void> {
    try {
      const liveLocation = await firstValueFrom(this.driversApi.liveLocation(driverId));
      this.liveLocation.set(liveLocation);
    } catch {
      // Silent - a transient failure to fetch the driver's live position shouldn't disrupt an already-rendered map.
    }
  }
}
