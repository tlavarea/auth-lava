import { decodePolyline } from './decode-polyline';

describe('decodePolyline', () => {
  it('returns an empty path for an empty string', () => {
    expect(decodePolyline('')).toEqual([]);
  });

  // The canonical worked example from Google's Encoded Polyline Algorithm Format docs:
  // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
  it('decodes Google’s reference example', () => {
    const path = decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@');

    expect(path).toHaveLength(3);
    expect(path[0].lat).toBeCloseTo(38.5, 5);
    expect(path[0].lng).toBeCloseTo(-120.2, 5);
    expect(path[1].lat).toBeCloseTo(40.7, 5);
    expect(path[1].lng).toBeCloseTo(-120.95, 5);
    expect(path[2].lat).toBeCloseTo(43.252, 5);
    expect(path[2].lng).toBeCloseTo(-126.453, 5);
  });
});
