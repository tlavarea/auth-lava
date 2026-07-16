import { shipmentStatusVariant } from './shipment-status';

describe('shipmentStatusVariant', () => {
  it('maps Open to success', () => {
    expect(shipmentStatusVariant('Open')).toBe('success');
  });

  it('maps Awaiting Award to info', () => {
    expect(shipmentStatusVariant('Awaiting Award')).toBe('info');
  });

  it('maps ACCEPTED to success', () => {
    expect(shipmentStatusVariant('ACCEPTED')).toBe('success');
  });

  it('maps EXPIRED to destructive', () => {
    expect(shipmentStatusVariant('EXPIRED')).toBe('destructive');
  });

  it('maps an unrecognized status to secondary', () => {
    expect(shipmentStatusVariant('Cancelled')).toBe('secondary');
  });

  it('is case-insensitive and trims whitespace', () => {
    expect(shipmentStatusVariant('  open  ')).toBe('success');
    expect(shipmentStatusVariant('awaiting award')).toBe('info');
    expect(shipmentStatusVariant('expired')).toBe('destructive');
  });
});
