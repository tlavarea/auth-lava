import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ThemeToggle } from './theme-toggle';

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

describe('ThemeToggle', () => {
  let fixture: ComponentFixture<ThemeToggle>;

  beforeEach(async () => {
    document.documentElement.classList.remove('dark');
    stubLocalStorage();

    await TestBed.configureTestingModule({
      imports: [ThemeToggle],
    }).compileComponents();

    fixture = TestBed.createComponent(ThemeToggle);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('toggles the theme and updates the button label when clicked', () => {
    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Switch to dark theme"]');
    expect(toggle).toBeTruthy();

    toggle.click();
    fixture.detectChanges();

    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(fixture.nativeElement.querySelector('button[aria-label="Switch to light theme"]')).toBeTruthy();
  });
});
