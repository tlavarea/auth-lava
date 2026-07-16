import { shipmentRankVariant } from './shipment-rank';

describe('shipmentRankVariant', () => {
  it('is success at and below 20', () => {
    expect(shipmentRankVariant('1')).toBe('success');
    expect(shipmentRankVariant('20')).toBe('success');
  });

  it('is warning from 21 through 70', () => {
    expect(shipmentRankVariant('21')).toBe('warning');
    expect(shipmentRankVariant('70')).toBe('warning');
  });

  it('is destructive at and above 71', () => {
    expect(shipmentRankVariant('71')).toBe('destructive');
    expect(shipmentRankVariant('194')).toBe('destructive');
  });

  it('falls back to secondary for a non-numeric rank', () => {
    expect(shipmentRankVariant('N/A')).toBe('secondary');
  });
});
