import { shipmentLocationState } from './shipment-location';

describe('shipmentLocationState', () => {
  it('extracts the trailing state abbreviation from a "code, CITY,ST" string', () => {
    expect(shipmentLocationState('664300240, SHEPPARD AFB,TX')).toBe('TX');
  });

  it('handles multi-word city names with embedded commas in the code segment', () => {
    expect(shipmentLocationState('879791250, FORT HUNTER LIGGETT W81W0U,CA')).toBe('CA');
  });

  it('falls back to the trimmed input when there is no comma', () => {
    expect(shipmentLocationState('NM')).toBe('NM');
  });
});
