import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { GuestShell } from './guest-shell';

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

@Component({ template: 'stub page' })
class StubRoutePage {}

describe('GuestShell', () => {
  let fixture: ComponentFixture<GuestShell>;

  beforeEach(async () => {
    document.documentElement.classList.remove('dark');
    stubLocalStorage();

    await TestBed.configureTestingModule({
      imports: [GuestShell],
      providers: [provideRouter([{ path: '', component: StubRoutePage }])],
    }).compileComponents();

    fixture = TestBed.createComponent(GuestShell);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the theme toggle', () => {
    const toggle: HTMLButtonElement | null = fixture.nativeElement.querySelector('button[aria-label$="theme"]');
    expect(toggle).toBeTruthy();
  });
});
