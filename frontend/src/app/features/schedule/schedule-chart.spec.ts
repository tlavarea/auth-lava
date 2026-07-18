import { buildWeekDayTicks, DAY_MS, percentForTime, startOfDayMs, WEEK_DAYS, WEEK_MS } from './schedule-chart';

describe('schedule-chart', () => {
  it('buildWeekDayTicks() produces one tick per day of the rolling week, starting with "Today"', () => {
    const now = new Date(2026, 6, 17, 12, 0, 0).getTime(); // Friday, July 17 2026

    const ticks = buildWeekDayTicks(now);

    expect(ticks).toHaveLength(WEEK_DAYS);
    expect(ticks[0]).toEqual({ dayIndex: 0, percent: 0, label: 'Today', isToday: true });
    expect(ticks[1]).toEqual({ dayIndex: 1, percent: (1 / WEEK_DAYS) * 100, label: 'Sat 7/18', isToday: false });
    expect(ticks[6]).toEqual({ dayIndex: 6, percent: (6 / WEEK_DAYS) * 100, label: 'Thu 7/23', isToday: false });
  });

  it('percentForTime() positions a time within the week proportionally', () => {
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
});
