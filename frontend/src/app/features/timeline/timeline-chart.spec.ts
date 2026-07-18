import { buildHourTicks, percentForTime, startOfDayMs } from './timeline-chart';

describe('timeline-chart', () => {
  it('buildHourTicks() produces 25 ticks (0-24) with labels only every 3rd hour', () => {
    const ticks = buildHourTicks();

    expect(ticks).toHaveLength(25);
    expect(ticks[0]).toEqual({ hour: 0, percent: 0, label: 'M' });
    expect(ticks[12]).toEqual({ hour: 12, percent: 50, label: 'N' });
    expect(ticks[24]).toEqual({ hour: 24, percent: 100, label: 'M' });
    expect(ticks[1].label).toBe('');
    expect(ticks[3].label).toBe('3');
  });

  it('percentForTime() positions a time within the day proportionally', () => {
    const dayStart = new Date(2026, 6, 17, 0, 0, 0).getTime();
    const noon = new Date(2026, 6, 17, 12, 0, 0).getTime();

    expect(percentForTime(noon, dayStart)).toBe(50);
  });

  it('percentForTime() clamps a time before the day start to 0', () => {
    const dayStart = new Date(2026, 6, 17, 0, 0, 0).getTime();
    const yesterday = new Date(2026, 6, 16, 12, 0, 0).getTime();

    expect(percentForTime(yesterday, dayStart)).toBe(0);
  });

  it('percentForTime() clamps a time after the day end to 100', () => {
    const dayStart = new Date(2026, 6, 17, 0, 0, 0).getTime();
    const tomorrow = new Date(2026, 6, 18, 12, 0, 0).getTime();

    expect(percentForTime(tomorrow, dayStart)).toBe(100);
  });

  it('startOfDayMs() truncates to midnight of the same local day', () => {
    const now = new Date(2026, 6, 17, 15, 30, 0).getTime();

    expect(startOfDayMs(now)).toBe(new Date(2026, 6, 17, 0, 0, 0).getTime());
  });
});
