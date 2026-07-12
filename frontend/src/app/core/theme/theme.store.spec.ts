import { TestBed } from '@angular/core/testing';

import { ThemeStore } from './theme.store';

// jsdom throws SecurityError for localStorage under the test runner's opaque (about:blank)
// origin, so stub it with an in-memory implementation rather than relying on jsdom's own.
function stubLocalStorage(): void {
  const store = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => store.set(key, value),
      removeItem: (key: string) => store.delete(key),
    },
  });
}

describe('ThemeStore', () => {
  beforeEach(() => {
    document.documentElement.classList.remove('dark');
    stubLocalStorage();
    TestBed.configureTestingModule({});
  });

  it('initialize() reads the theme already applied to <html> by the inline no-flash script', () => {
    document.documentElement.classList.add('dark');

    const store = TestBed.inject(ThemeStore);
    store.initialize();

    expect(store.theme()).toBe('dark');
  });

  it('initialize() defaults to light when no dark class is present', () => {
    const store = TestBed.inject(ThemeStore);
    store.initialize();

    expect(store.theme()).toBe('light');
  });

  it('toggle() flips the theme, updates the <html> class, and persists to localStorage', () => {
    const store = TestBed.inject(ThemeStore);
    store.initialize();
    expect(store.theme()).toBe('light');

    store.toggle();

    expect(store.theme()).toBe('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(window.localStorage.getItem('theme')).toBe('dark');

    store.toggle();

    expect(store.theme()).toBe('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(window.localStorage.getItem('theme')).toBe('light');
  });
});
