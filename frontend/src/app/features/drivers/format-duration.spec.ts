import { formatDurationMs } from './format-duration';

describe('formatDurationMs', () => {
  it('formats hours and zero-padded minutes', () => {
    expect(formatDurationMs(5 * 3_600_000 + 38 * 60_000)).toBe('5:38');
    expect(formatDurationMs(22 * 3_600_000 + 58 * 60_000)).toBe('22:58');
  });

  it('formats sub-hour durations without a leading hour digit', () => {
    expect(formatDurationMs(3 * 60_000)).toBe('0:03');
  });

  it('treats null as 0:00', () => {
    expect(formatDurationMs(null)).toBe('0:00');
  });

  it('treats zero and negative durations as 0:00', () => {
    expect(formatDurationMs(0)).toBe('0:00');
    expect(formatDurationMs(-1_000)).toBe('0:00');
  });
});
