import { Component, computed, DestroyRef, inject, input, InputSignal, signal, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { GoogleMap, MapMarker } from '@angular/google-maps';
import { timer } from 'rxjs';

const DEFAULT_ZOOM = 13;
// Arrow marker scale - tuned to read clearly at DEFAULT_ZOOM without overwhelming nearby map labels.
const ARROW_SCALE = 6;

// Dead-reckoning + smoothing tuning, see the class doc below.
const ANIMATION_TICK_MS = 200; // 5 updates/sec - smooth to the eye, cheap to run continuously.
const LERP_FACTOR = 0.3; // fraction of the remaining distance to a new fix closed per tick - converges in ~1-1.5s.
const MAX_EXTRAPOLATION_SECONDS = 30; // caps drift if polling stalls (e.g. a network hiccup) rather than running away.
const METERS_PER_DEGREE_LATITUDE = 111_320;
const MPH_TO_METERS_PER_SECOND = 0.44704;

type Fix = { lat: number; lng: number; headingDegrees: number | null; speedMph: number | null; atMs: number };

/**
 * Renders the driver's position as a heading-rotated arrow (falling back to a plain pin with no heading fix) that
 * moves continuously rather than snapping to each new poll. Between polls, the displayed position is dead-reckoned
 * forward from the last known fix's speed/heading (frozen if the vehicle is stopped); when a new fix arrives, the
 * displayed position eases (lerps) toward it instead of jumping, since the actual position can differ slightly from
 * the dead-reckoned guess. This approximates Samsara's own live-view smoothness without needing a persisted location
 * trail or a much faster poll - see `driver-detail.page.ts`'s `pollLiveLocation` cadence.
 */
@Component({
  selector: 'app-driver-location-map',
  imports: [GoogleMap, MapMarker],
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
      @if (markerPosition(); as position) {
        @if (markerIcon(); as icon) {
          <map-marker [position]="position" [icon]="icon" />
        } @else {
          <map-marker [position]="position" />
        }
      }
    </google-map>
  `,
})
export class DriverLocationMap {
  readonly latitude: InputSignal<number | null> = input<number | null>(null);
  readonly longitude: InputSignal<number | null> = input<number | null>(null);
  // Degrees clockwise from true north, as reported by Samsara's GPS fix - null when the vehicle has no heading fix
  // (e.g. stationary), in which case the marker renders as a plain (unrotated) pin rather than a directional arrow.
  readonly heading: InputSignal<number | null> = input<number | null>(null);
  // Miles per hour, as reported by Samsara's GPS fix - drives the dead-reckoning animation between polls; null or 0
  // means stopped (no extrapolation).
  readonly speed: InputSignal<number | null> = input<number | null>(null);
  readonly formattedLocation: InputSignal<string | null> = input<string | null>(null);

  private readonly destroyRef: DestroyRef = inject(DestroyRef);

  protected readonly DEFAULT_ZOOM = DEFAULT_ZOOM;

  // No zoomControlOptions.position override - Google's modern Maps JS API loads enums like ControlPosition
  // asynchronously as part of the "maps" library, which isn't guaranteed ready this early (a component field
  // initializer runs at construction, well before <google-map>'s own library-loading completes) - referencing
  // google.maps.ControlPosition here threw "ControlPosition is undefined" in production. zoomControl: true alone
  // needs no enum lookup and gets Google's own sensible default position (bottom-right on desktop).
  protected readonly mapOptions: google.maps.MapOptions = { zoomControl: true };

  protected readonly ariaLabel: Signal<string> = computed(() => this.formattedLocation() ?? 'Driver location map');

  // The latest fix, derived (not an effect) so it updates synchronously - and its `atMs` is stamped exactly when
  // latitude/longitude/heading/speed actually change, since a `computed` only re-runs when a tracked dependency does.
  // That's precisely the "anchor reset on every new poll" semantic dead reckoning needs, with no effect-scheduling
  // timing gap.
  private readonly fix: Signal<Fix | null> = computed(() => {
    const lat = this.latitude();
    const lng = this.longitude();
    if (lat === null || lng === null) {
      return null;
    }
    return { lat, lng, headingDegrees: this.heading(), speedMph: this.speed(), atMs: Date.now() };
  });

  // Ticked forward every ANIMATION_TICK_MS by dead-reckoning off `fix` - null until the first tick runs.
  private readonly tickedPosition = signal<google.maps.LatLngLiteral | null>(null);

  // Falls back to `fix` directly (synchronously, before the first animation tick has run) so the marker appears
  // immediately rather than flashing empty for up to ANIMATION_TICK_MS on first load.
  protected readonly markerPosition: Signal<google.maps.LatLngLiteral | null> = computed(() => {
    const ticked = this.tickedPosition();
    if (ticked !== null) {
      return ticked;
    }
    const fix = this.fix();
    return fix === null ? null : { lat: fix.lat, lng: fix.lng };
  });

  // google-map always needs a center, even before a location has loaded - falls back to (0, 0) until then.
  protected readonly center: Signal<google.maps.LatLngLiteral> = computed(
    () => this.markerPosition() ?? { lat: 0, lng: 0 }
  );

  // A rotated arrow (Samsara's own "live view" convention) when a heading fix is available, otherwise null - falls
  // back to MapMarker's default pin icon rather than an arrow pointing in a meaningless direction.
  protected readonly markerIcon: Signal<google.maps.Symbol | null> = computed(() => {
    const heading = this.heading();
    if (heading === null) {
      return null;
    }
    return {
      path: google.maps.SymbolPath.FORWARD_CLOSED_ARROW,
      rotation: heading,
      scale: ARROW_SCALE,
      strokeColor: '#ffffff',
      strokeWeight: 1,
      fillColor: '#2563eb',
      fillOpacity: 1,
    };
  });

  constructor() {
    timer(0, ANIMATION_TICK_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.tick());
  }

  private tick(): void {
    const fix = this.fix();
    if (fix === null) {
      this.tickedPosition.set(null);
      return;
    }

    const target = extrapolate(fix);
    const current = this.tickedPosition();
    this.tickedPosition.set(current === null ? target : lerp(current, target, LERP_FACTOR));
  }
}

function extrapolate(fix: Fix): google.maps.LatLngLiteral {
  const speedMph = fix.speedMph ?? 0;
  if (speedMph <= 0 || fix.headingDegrees === null) {
    return { lat: fix.lat, lng: fix.lng };
  }

  const dtSeconds = Math.min((Date.now() - fix.atMs) / 1000, MAX_EXTRAPOLATION_SECONDS);
  const metersTraveled = speedMph * MPH_TO_METERS_PER_SECOND * dtSeconds;
  const headingRadians = (fix.headingDegrees * Math.PI) / 180;
  const metersPerDegreeLongitude = METERS_PER_DEGREE_LATITUDE * Math.cos((fix.lat * Math.PI) / 180);

  return {
    lat: fix.lat + (metersTraveled * Math.cos(headingRadians)) / METERS_PER_DEGREE_LATITUDE,
    lng: fix.lng + (metersTraveled * Math.sin(headingRadians)) / metersPerDegreeLongitude,
  };
}

function lerp(
  from: google.maps.LatLngLiteral,
  to: google.maps.LatLngLiteral,
  factor: number
): google.maps.LatLngLiteral {
  return { lat: from.lat + (to.lat - from.lat) * factor, lng: from.lng + (to.lng - from.lng) * factor };
}
