// Decodes Google's polyline encoding (https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
// by hand rather than via `google.maps.geometry.encoding.decodePath` - that namespace only exists once the "geometry"
// library finishes its own async load via `google.maps.importLibrary('geometry')`, the same class of "not guaranteed
// ready yet" gotcha `driver-location-map.ts` already hit with `SymbolPath`/`ControlPosition`. The algorithm itself is
// small, standard, and has no async dependency, so implementing it directly sidesteps that risk entirely.
export function decodePolyline(encoded: string): google.maps.LatLngLiteral[] {
  const path: google.maps.LatLngLiteral[] = [];
  let index = 0;
  let lat = 0;
  let lng = 0;

  while (index < encoded.length) {
    lat += decodeSignedDelta();
    lng += decodeSignedDelta();
    path.push({ lat: lat / 1e5, lng: lng / 1e5 });
  }

  return path;

  // Bitwise ops are inherent to this bit-packed varint format, not a style choice - disabled for this block only.
  /* eslint-disable no-bitwise */
  function decodeSignedDelta(): number {
    let result = 0;
    let shift = 0;
    let byte: number;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    return result & 1 ? ~(result >> 1) : result >> 1;
  }
  /* eslint-enable no-bitwise */
}
