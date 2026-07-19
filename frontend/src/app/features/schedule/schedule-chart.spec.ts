import {
  buildDayTicks,
  DAY_MS,
  DEFAULT_RANGE_DAYS,
  formatCityState,
  formatDateRange,
  percentForTime,
  startOfDayMs,
} from './schedule-chart';

const WEEK_MS = DEFAULT_RANGE_DAYS * DAY_MS;

describe('schedule-chart', () => {
  it('buildDayTicks() produces one tick per day of the range, labeling the day matching nowMs "Today"', () => {
    const now = new Date(2026, 6, 17, 12, 0, 0).getTime(); // Friday, July 17 2026
    const weekStart = new Date(2026, 6, 17, 0, 0, 0).getTime();

    const ticks = buildDayTicks(weekStart, DEFAULT_RANGE_DAYS, now);

    expect(ticks).toHaveLength(DEFAULT_RANGE_DAYS);
    expect(ticks[0]).toEqual({ dayIndex: 0, percent: 0, label: 'Today', isToday: true });
    expect(ticks[1]).toEqual({
      dayIndex: 1,
      percent: (1 / DEFAULT_RANGE_DAYS) * 100,
      label: 'Sat 7/18',
      isToday: false,
    });
    expect(ticks[6]).toEqual({
      dayIndex: 6,
      percent: (6 / DEFAULT_RANGE_DAYS) * 100,
      label: 'Thu 7/23',
      isToday: false,
    });
  });

  it('buildDayTicks() labels no day "Today" when the visible range does not contain nowMs', () => {
    const now = new Date(2026, 6, 17, 12, 0, 0).getTime(); // Friday, July 17 2026
    const nextWeekStart = new Date(2026, 6, 24, 0, 0, 0).getTime();

    const ticks = buildDayTicks(nextWeekStart, DEFAULT_RANGE_DAYS, now);

    expect(ticks.every((tick) => !tick.isToday)).toBe(true);
    expect(ticks[0].label).toBe('Fri 7/24');
  });

  it('buildDayTicks() defaults nowMs to the current time when omitted', () => {
    vi.setSystemTime(new Date(2026, 6, 17, 12, 0, 0));
    const weekStart = new Date(2026, 6, 17, 0, 0, 0).getTime();

    const ticks = buildDayTicks(weekStart, DEFAULT_RANGE_DAYS);

    expect(ticks[0].isToday).toBe(true);
    vi.useRealTimers();
  });

  it('buildDayTicks() supports a range longer than a week', () => {
    const rangeStart = new Date(2026, 6, 17, 0, 0, 0).getTime();

    const ticks = buildDayTicks(rangeStart, 14, rangeStart);

    expect(ticks).toHaveLength(14);
    expect(ticks[13].percent).toBe((13 / 14) * 100);
  });

  it('percentForTime() positions a time within the default range proportionally', () => {
    const weekStart = new Date(2026, 6, 17, 0, 0, 0).getTime();
    const midWeek = weekStart + WEEK_MS / 2;

    expect(percentForTime(midWeek, weekStart)).toBe(50);
  });

  it('percentForTime() clamps a time before the range start to 0', () => {
    const weekStart = new Date(2026, 6, 17, 0, 0, 0).getTime();
    const beforeWeek = weekStart - DAY_MS;

    expect(percentForTime(beforeWeek, weekStart)).toBe(0);
  });

  it('percentForTime() clamps a time after the range end to 100', () => {
    const weekStart = new Date(2026, 6, 17, 0, 0, 0).getTime();
    const afterWeek = weekStart + WEEK_MS + DAY_MS;

    expect(percentForTime(afterWeek, weekStart)).toBe(100);
  });

  it('percentForTime() accepts a custom range length', () => {
    const dayStart = new Date(2026, 6, 17, 0, 0, 0).getTime();
    const noon = new Date(2026, 6, 17, 12, 0, 0).getTime();

    expect(percentForTime(noon, dayStart, DAY_MS)).toBe(50);
  });

  it('startOfDayMs() truncates to midnight of the same local day', () => {
    const now = new Date(2026, 6, 17, 15, 30, 0).getTime();

    expect(startOfDayMs(now)).toBe(new Date(2026, 6, 17, 0, 0, 0).getTime());
  });

  it('formatCityState() shortens a full street address to "City, ST"', () => {
    expect(formatCityState('6390 N Alsup Rd, Litchfield Park, AZ 85340')).toBe('Litchfield Park, AZ');
  });

  it('formatCityState() handles a two-segment "City, ST zip" address', () => {
    expect(formatCityState('Bessemer, AL 35020')).toBe('Bessemer, AL');
  });

  it('formatCityState() falls back to the trimmed input when it has no comma-separated segments', () => {
    expect(formatCityState('  Phoenix  ')).toBe('Phoenix');
  });

  it('formatCityState() returns null for a null address', () => {
    expect(formatCityState(null)).toBeNull();
  });

  it('formatDateRange() renders a [start, end] range as "MM/DD/YYYY - MM/DD/YYYY"', () => {
    const start = new Date(2026, 6, 14);
    const end = new Date(2026, 7, 31);

    expect(formatDateRange([start, end])).toBe('07/14/2026 - 08/31/2026');
  });

  it('formatDateRange() returns an empty string when nothing is selected', () => {
    expect(formatDateRange(undefined)).toBe('');
    expect(formatDateRange([undefined, undefined])).toBe('');
  });
});
