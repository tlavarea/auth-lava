import { startOfTodayIso } from './date-utils';

describe('startOfTodayIso', () => {
  it('returns midnight of the current local day as an ISO string', () => {
    const fixedNow = new Date(2026, 6, 16, 14, 25, 0); // Jul 16, 2026, 2:25 PM local time
    vi.setSystemTime(fixedNow);

    const expected = new Date(2026, 6, 16, 0, 0, 0, 0).toISOString();
    expect(startOfTodayIso()).toBe(expected);

    vi.useRealTimers();
  });
});
