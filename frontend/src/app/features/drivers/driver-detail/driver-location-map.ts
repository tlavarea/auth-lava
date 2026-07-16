import { Component, computed, input, InputSignal, Signal } from '@angular/core';
import { GoogleMap, MapMarker } from '@angular/google-maps';

const DEFAULT_ZOOM = 13;

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
      [attr.aria-label]="ariaLabel()">
      @if (markerPosition(); as position) {
        <map-marker [position]="position" />
      }
    </google-map>
  `,
})
export class DriverLocationMap {
  readonly latitude: InputSignal<number | null> = input<number | null>(null);
  readonly longitude: InputSignal<number | null> = input<number | null>(null);
  readonly formattedLocation: InputSignal<string | null> = input<string | null>(null);

  protected readonly DEFAULT_ZOOM = DEFAULT_ZOOM;

  protected readonly ariaLabel: Signal<string> = computed(() => this.formattedLocation() ?? 'Driver location map');

  protected readonly markerPosition: Signal<google.maps.LatLngLiteral | null> = computed(() => {
    const latitude = this.latitude();
    const longitude = this.longitude();
    return latitude !== null && longitude !== null ? { lat: latitude, lng: longitude } : null;
  });

  // google-map always needs a center, even before a location has loaded - falls back to (0, 0) until then.
  protected readonly center: Signal<google.maps.LatLngLiteral> = computed(
    () => this.markerPosition() ?? { lat: 0, lng: 0 }
  );
}
