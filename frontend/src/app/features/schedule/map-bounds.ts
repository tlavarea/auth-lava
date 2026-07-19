// Computes the midpoint of a set of points' bounding box using plain arithmetic - no dependency on
// `google.maps.LatLngBounds`, which only exists once the async-loaded Maps JS script finishes initializing (the same
// "not guaranteed ready yet" gotcha decode-polyline.ts sidesteps for the "geometry" library). Letting the initial
// `[center]` binding use this instead of a single fallback position means the map opens already framed roughly on the
// whole route, rather than visibly panning from one endpoint to a full-route view once the map instance shows up.
export function computeBoundsCenter(points: google.maps.LatLngLiteral[]): google.maps.LatLngLiteral | null {
  if (points.length === 0) {
    return null;
  }

  let minLat = points[0].lat;
  let maxLat = points[0].lat;
  let minLng = points[0].lng;
  let maxLng = points[0].lng;

  for (const point of points) {
    minLat = Math.min(minLat, point.lat);
    maxLat = Math.max(maxLat, point.lat);
    minLng = Math.min(minLng, point.lng);
    maxLng = Math.max(maxLng, point.lng);
  }

  return { lat: (minLat + maxLat) / 2, lng: (minLng + maxLng) / 2 };
}
