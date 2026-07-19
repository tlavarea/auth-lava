import { Component, computed, effect, inject, input, InputSignal, signal, Signal } from '@angular/core';
import { GoogleMap, MapMarker, MapPolyline } from '@angular/google-maps';
import { firstValueFrom } from 'rxjs';

import { decodePolyline } from './decode-polyline';
import { computeBoundsCenter } from './map-bounds';
import { ScheduleApi } from './schedule-api';
import { ManifestDriverLocation, ManifestRoute, ManifestSegment, ManifestStop } from './schedule.models';

const DEFAULT_ZOOM = 6;
// A ceiling on how far the automatic route fit is allowed to zoom in - defends against Google Maps' well-known
// fitBounds behavior of snapping to its maximum zoom (21, street level) when the bounds it's given are degenerate
// (a single point, or two points close enough together to round to one) - which a manifest with only one geocoded
// stop and no starting position would otherwise trigger. 12 is roughly "a single metro area" - loose enough not to
// clip a real, deliberately tight local route, tight enough that a degenerate fit is still clearly visibly wrong.
const MAX_AUTO_FIT_ZOOM = 12;
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
 * Vektor reported one, and the driving path visiting all of them in order) plus the driver's position as of when the
 * manifest was opened, for the map panel that opens below the Schedule grid when a manifest segment is clicked. A
 * self-contained sibling to DriverLocationMap rather than a reuse of it - that component is purpose-built for a
 * single continuously-polled marker with dead-reckoning animation in a layout suited to driver-detail's
 * absolute-fill container, and is where a dispatcher goes (via the driver's name) for actual live tracking. This
 * component instead fetches the manifest-scoped, Vektor-sourced equivalent once (ScheduleApi.driverLocation, keyed
 * by manifestNumber rather than a driverId input) - the same source ManifestRouteServiceImpl's route-splice uses, so
 * the marker and the drawn route always agree on where the driver was - and does not re-fetch on a timer. It ports
 * DriverLocationMap's marker/heading gotchas (see the icon constants above) but skips the lerp/dead-reckoning
 * animation, which only makes sense for a marker that keeps moving.
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
      [attr.aria-label]="ariaLabel()"
      (idle)="onMapIdle()"
      (mapInitialized)="googleMap.set($event)">
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
  readonly manifest: InputSignal<ManifestSegment> = input.required<ManifestSegment>();
  // Fetched by the parent (ScheduleStore) rather than by this component, so it and ScheduleManifestDetail render the
  // same data from a single request instead of each independently fetching the same manifest's route.
  readonly route: InputSignal<ManifestRoute | null> = input.required<ManifestRoute | null>();

  private readonly scheduleApi: ScheduleApi = inject(ScheduleApi);

  // Set from the template's (mapInitialized) binding, not read via viewChild(GoogleMap)'s `.googleMap` property -
  // that property is assigned imperatively, outside Angular's reactivity, once the underlying Google Maps JS
  // instance actually finishes constructing (see @angular/google-maps' GoogleMap._initialize), which happens later
  // than the <google-map> child component itself becoming queryable. A signal keyed on mapInitialized is the only
  // way for the fitBounds effect below to reliably re-run once the map is *actually* ready, rather than possibly
  // observing it as still-null on its one and only run and never getting a second chance.
  protected readonly googleMap = signal<google.maps.Map | null>(null);

  // Plain field, not a signal - read/written from inside the same effect that also reads routePoints(), and only
  // needs to gate that effect's body, not participate in Angular's own dependency tracking (see the constructor's
  // fitBounds effect for why this exists).
  private fitBoundsForManifestNumber: number | null = null;
  // Sends the next (idle) event to onMapIdle's zoom-clamp check - set right before calling fitBounds, cleared as
  // soon as that check runs, so idle events from anything else (the map's initial construction, a dispatcher's own
  // manual zoom/pan) are left alone.
  private pendingZoomClampCheck = false;

  protected readonly DEFAULT_ZOOM = DEFAULT_ZOOM;
  // A starting position, when present, is the route's true origin - same play-button icon a first stop would get if
  // there were no starting position (see stopMarkers below).
  protected readonly startingPositionMarkerIcon = ORIGIN_MARKER_ICON;
  // No zoomControlOptions.position override - same async-loaded-enum gotcha as driver-location-map.ts's mapOptions.
  protected readonly mapOptions: google.maps.MapOptions = { zoomControl: true };
  protected readonly polylineOptions: google.maps.PolylineOptions = { strokeColor: '#2563eb', strokeWeight: 4 };

  // Set once, from a single fetch when the manifest is selected (see the constructor) - not polled, so this never
  // updates again while the panel stays open. A dispatcher who needs an up-to-date position clicks the driver's name
  // instead, which opens driver-detail's actual live-tracking map.
  private readonly driverLocationSnapshot = signal<ManifestDriverLocation | null>(null);

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
    const location = this.driverLocationSnapshot();
    if (location === null || location.latitude === null || location.longitude === null) {
      return null;
    }
    return { lat: location.latitude, lng: location.longitude };
  });

  protected readonly driverMarkerIcon: Signal<google.maps.Symbol | null> = computed(() => {
    const heading = this.driverLocationSnapshot()?.headingDegrees;
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
    effect(() => void this.fetchDriverLocation(this.manifest().manifestNumber));

    // Keeps the whole route (every stop and the starting position) in view rather than relying on a fixed zoom,
    // which could easily clip either end of a long-haul, multi-stop route - exactly once per manifest selection,
    // deliberately keyed off the route's own static points rather than allKnownPositions() (which also folds in the
    // driver's position): the driver's location fetch above is async and could resolve after this effect's first
    // run, so including it here could trigger a second, jarring re-fit once it lands. routePoints() only changes
    // when a different manifest (or its route) is selected, so gating on manifestNumber is mostly a defensive
    // no-op, not the load-bearing guard fitBoundsForManifestNumber used to be.
    effect(() => {
      const map = this.googleMap();
      const manifestNumber = this.manifest().manifestNumber;
      const points = this.routePoints();
      if (!map || points.length === 0 || this.fitBoundsForManifestNumber === manifestNumber) {
        return;
      }
      const bounds = new google.maps.LatLngBounds();
      points.forEach((point) => bounds.extend(point));
      this.pendingZoomClampCheck = true;
      map.fitBounds(bounds);
      this.fitBoundsForManifestNumber = manifestNumber;
    });
  }

  // The route's own static points (starting position + every geocoded stop) - used for the one-time auto-fit (see
  // the constructor) specifically because it does *not* include the driver's position, which can arrive later than
  // the initial fit (see the constructor's comment on why that's excluded).
  private routePoints(): google.maps.LatLngLiteral[] {
    const points: google.maps.LatLngLiteral[] = [];
    const startingPosition = this.startingPosition();
    if (startingPosition !== null) {
      points.push(startingPosition);
    }
    points.push(...this.stopMarkers().map((marker) => marker.position));
    return points;
  }

  private allKnownPositions(): google.maps.LatLngLiteral[] {
    const points = this.routePoints();
    const driver = this.driverPosition();
    if (driver !== null) {
      points.push(driver);
    }
    return points;
  }

  // Runs after every 'idle' event - which fires after any map settling, including our own programmatic fitBounds
  // call above, a dispatcher's manual zoom/pan, and the map's very first construction - but pendingZoomClampCheck
  // means the zoom-clamp logic itself only actually runs immediately following that one fitBounds call, not any of
  // the others (where clamping the zoom would fight the dispatcher's own input instead of protecting them from a
  // degenerate auto-fit).
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

  private async fetchDriverLocation(manifestNumber: number): Promise<void> {
    try {
      const driverLocation = await firstValueFrom(this.scheduleApi.driverLocation(manifestNumber));
      this.driverLocationSnapshot.set(driverLocation);
    } catch {
      // Silent - a transient failure to fetch the driver's position shouldn't disrupt an already-rendered map.
    }
  }
}
