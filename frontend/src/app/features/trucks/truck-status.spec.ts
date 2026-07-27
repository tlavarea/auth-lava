import { truckStatus, truckStatusBadge } from './truck-status';

describe('truckStatus', () => {
  it('is "moving" when the engine is on and ECU speed is nonzero', () => {
    expect(truckStatus('On', 62.5)).toBe('moving');
  });

  it('is "on" when the engine is on but ECU speed is zero or unavailable', () => {
    expect(truckStatus('On', 0)).toBe('on');
    expect(truckStatus('On', null)).toBe('on');
  });

  it('is "idle" when the engine is idling', () => {
    expect(truckStatus('Idle', null)).toBe('idle');
    expect(truckStatus('Idle', 0)).toBe('idle');
  });

  it('is "off" when the engine is off', () => {
    expect(truckStatus('Off', null)).toBe('off');
  });

  it('is "unknown" when there is no Samsara-matched engine state', () => {
    expect(truckStatus(null, null)).toBe('unknown');
  });
});

describe('truckStatusBadge', () => {
  it('gives "Moving" a solid-green badge distinct from "On"s light-green one', () => {
    const moving = truckStatusBadge('On', 62.5);
    const on = truckStatusBadge('On', null);

    expect(moving.label).toBe('Moving');
    expect(moving.variant).toBe('success');
    expect(moving.class).toContain('bg-success');

    expect(on.label).toBe('On');
    expect(on.variant).toBe('success');
    expect(on.class).toBe('');
  });
});
