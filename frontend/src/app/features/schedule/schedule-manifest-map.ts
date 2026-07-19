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
import { computeBoundsCenter } from './map-bounds';
import { ManifestRoute, ManifestSegment, ManifestStop } from './schedule.models';

const DEFAULT_ZOOM = 6;
// Matches driver-detail.page.ts's cadence for the same live-location call.
const LIVE_LOCATION_POLL_INTERVAL_MS = 15_000;
const ARROW_SCALE = 6;
const PIN_SCALE = 12;

// path/rotation use numeric SymbolPath literals rather than google.maps.SymbolPath.* - see driver-location-map.ts's
// comment on why: that enum only exists once the async-loaded "maps" library finishes initializing, which isn't
// guaranteed by the time these computeds first run. 0 is SymbolPath.CIRCLE, 1 is FORWARD_CLOSED_ARROW. The origin/
// destination icons below use hand-drawn SVG path strings instead - google.maps.Symbol accepts an arbitrary path,
// not just the enum, and there's no equivalent "play"/"stop" shape built in.
const PICKUP_MARKER_ICON: google.maps.Symbol = {
  path: 0,
  scale: PIN_SCALE,
  fillColor: '#16a34a',
  fillOpacity: 1,
  strokeColor: '#ffffff',
  strokeWeight: 2,
};
const DROPOFF_MARKER_ICON: google.maps.Symbol = {
  path: 0,
  scale: PIN_SCALE,
  fillColor: '#dc2626',
  fillOpacity: 1,
  strokeColor: '#ffffff',
  strokeWeight: 2,
};

// The route's true start and end get a distinct shape from the plain numbered pickup/dropoff circles in between -
// a play-button triangle for the origin (the starting position if Vektor reported one, otherwise the first stop) and
// a stop-button square for the destination (always the last stop). Paths are hand-drawn around the origin so no
// anchor override is needed; scale is tuned separately from PIN_SCALE since a path with ~6-8 unit coordinates reads
// differently than the built-in circle at the same scale value.
const ORIGIN_MARKER_ICON: google.maps.Symbol = {
  path: 'M -5,-7 L 7,0 L -5,7 Z',
  scale: 1.8,
  fillColor: '#16a34a',
  fillOpacity: 1,
  strokeColor: '#ffffff',
  strokeWeight: 2,
};
const DESTINATION_MARKER_ICON: google.maps.Symbol = {
  path: 'M -6,-6 L 6,-6 L 6,6 L -6,6 Z',
  scale: 1.8,
  fillColor: '#dc2626',
  fillOpacity: 1,
  strokeColor: '#ffffff',
  strokeWeight: 2,
};
const STOP_MARKER_LABEL_COLOR = '#ffffff';

type StopMarker = {
  stop: ManifestStop;
  position: google.maps.LatLngLiteral;
  icon: google.maps.Symbol;
  label: google.maps.MarkerLabel;
};

/**
 * Renders a manifest's full route (a numbered marker per pickup/dropoff stop, the truck's starting position if
 * Vektor reported one, and the driving path visiting all of them in order) plus the driver's current position, for
 * the map panel that opens below the Schedule grid when a manifest segment is clicked. A self-contained sibling to
 * DriverLocationMap rather than a reuse of it - that component is purpose-built for a single live marker with
 * dead-reckoning animation in a layout suited to driver-detail's absolute-fill container. This component ports its
 * marker/heading gotchas (see the icon constants above) but skips the lerp/dead-reckoning animation, since this panel
 * doesn't need per-200ms smoothness - the driver marker just moves to its latest polled position on each tick.
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
      @if (startingPosition(); as position) {
        <map-marker title="Starting position" [position]="position" [icon]="startingPositionMarkerIcon" />
      }
      @for (marker of stopMarkers(); track marker.stop.sequenceNumber) {
        <map-marker
          [title]="(marker.stop.stopType === 'PICKUP' ? 'Pickup' : 'Dropoff') + ' ' + marker.stop.sequenceNumber"
          [position]="marker.position"
          [icon]="marker.icon"
          [label]="marker.label" />
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
  // Fetched by the parent (ScheduleStore) rather than by this component, so it and ScheduleManifestDetail render the
  // same data from a single request instead of each independently fetching the same manifest's route.
  readonly route: InputSignal<ManifestRoute | null> = input.required<ManifestRoute | null>();

  private readonly driversApi: DriversApi = inject(DriversApi);
  private readonly destroyRef: DestroyRef = inject(DestroyRef);
  private readonly mapRef = viewChild(GoogleMap);

  protected readonly DEFAULT_ZOOM = DEFAULT_ZOOM;
  // A starting position, when present, is the route's true origin - same play-button icon a first stop would get if
  // there were no starting position (see stopMarkers below).
  protected readonly startingPositionMarkerIcon = ORIGIN_MARKER_ICON;
  // No zoomControlOptions.position override - same async-loaded-enum gotcha as driver-location-map.ts's mapOptions.
  protected readonly mapOptions: google.maps.MapOptions = { zoomControl: true };
  protected readonly polylineOptions: google.maps.PolylineOptions = { strokeColor: '#2563eb', strokeWeight: 4 };

  private readonly liveLocation = signal<DriverLiveLocationResponse | null>(null);

  protected readonly decodedPath: Signal<google.maps.LatLngLiteral[] | null> = computed(() => {
    const route = this.route();
    return route === null ? null : decodePolyline(route.encodedPolyline);
  });

  protected readonly startingPosition: Signal<google.maps.LatLngLiteral | null> = computed(() => {
    const startingPosition = this.route()?.startingPosition ?? null;
    if (startingPosition === null || startingPosition.latitude === null || startingPosition.longitude === null) {
      return null;
    }
    return { lat: startingPosition.latitude, lng: startingPosition.longitude };
  });

  // The first stop only gets the origin (play) icon when there's no starting position marker already claiming that
  // role; the last stop always gets the destination (stop) icon, since a starting position is never the route's end.
  protected readonly stopMarkers: Signal<StopMarker[]> = computed(() => {
    const validStops = (this.route()?.stops ?? []).filter((stop) => stop.latitude !== null && stop.longitude !== null);
    const originIsStartingPosition = this.startingPosition() !== null;
    return validStops.map((stop, index) => {
      const isOrigin = !originIsStartingPosition && index === 0;
      const isDestination = index === validStops.length - 1;
      let icon: google.maps.Symbol;
      if (isOrigin) {
        icon = ORIGIN_MARKER_ICON;
      } else if (isDestination) {
        icon = DESTINATION_MARKER_ICON;
      } else {
        icon = stop.stopType === 'PICKUP' ? PICKUP_MARKER_ICON : DROPOFF_MARKER_ICON;
      }
      return {
        stop,
        position: { lat: stop.latitude!, lng: stop.longitude! },
        icon,
        label: { text: String(stop.sequenceNumber), color: STOP_MARKER_LABEL_COLOR, fontWeight: 'bold' },
      };
    });
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

  // google-map always needs a center, even before a route/location has loaded. Rather than centering on a single
  // fallback position (which used to be just the origin, and visibly panned to a whole-route view later once
  // fitBounds could run - see the effect below), this centers on the midpoint of every known position right away
  // using plain arithmetic (computeBoundsCenter), so there's nothing for the later fitBounds call to visibly jump
  // away from.
  protected readonly center: Signal<google.maps.LatLngLiteral> = computed(
    () => computeBoundsCenter(this.allKnownPositions()) ?? { lat: 0, lng: 0 }
  );

  protected readonly ariaLabel: Signal<string> = computed(() => {
    const manifest = this.manifest();
    const from = manifest.origin ?? 'unknown origin';
    const to = manifest.destination ?? 'unknown destination';
    return `Route map from ${from} to ${to}`;
  });

  constructor() {
    effect(() => void this.pollLiveLocation(this.driverId()));

    timer(LIVE_LOCATION_POLL_INTERVAL_MS, LIVE_LOCATION_POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.pollLiveLocation(this.driverId()));

    // Keeps the whole route (every stop, the starting position, and the driver's position, if known) in view rather
    // than relying on a fixed zoom, which could easily clip either end of a long-haul, multi-stop route.
    effect(() => {
      const map = this.mapRef()?.googleMap;
      const points = this.allKnownPositions();
      if (!map || points.length === 0) {
        return;
      }
      const bounds = new google.maps.LatLngBounds();
      points.forEach((point) => bounds.extend(point));
      map.fitBounds(bounds);
    });
  }

  private allKnownPositions(): google.maps.LatLngLiteral[] {
    const points: google.maps.LatLngLiteral[] = [];
    const startingPosition = this.startingPosition();
    if (startingPosition !== null) {
      points.push(startingPosition);
    }
    points.push(...this.stopMarkers().map((marker) => marker.position));
    const driver = this.driverPosition();
    if (driver !== null) {
      points.push(driver);
    }
    return points;
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
