import { OverlayContainer } from '@angular/cdk/overlay';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AuthStore } from '@core/auth/auth.store';
import { AppShell } from './app-shell';

@Component({ template: '' })
class StubRoutePage {}

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

describe('AppShell', () => {
  let component: AppShell;
  let fixture: ComponentFixture<AppShell>;
  let httpMock: HttpTestingController;
  let router: Router;
  let overlayContainerElement: HTMLElement;

  beforeEach(async () => {
    document.documentElement.classList.remove('dark');
    stubLocalStorage();

    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'login', component: StubRoutePage },
          { path: 'profile', component: StubRoutePage },
        ]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    overlayContainerElement = TestBed.inject(OverlayContainer).getContainerElement();

    const authStore = TestBed.inject(AuthStore);
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: true, mfaEnabled: false, authorities: [] });
    await bootstrapPromise;

    fixture = TestBed.createComponent(AppShell);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  function openAccountMenu(): void {
    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector('button[aria-label="Account menu"]');
    trigger.click();
    fixture.detectChanges();
  }

  it('shows the signed-in user email and a profile link in the account menu', () => {
    openAccountMenu();

    expect(overlayContainerElement.textContent).toContain('a@b.com');
    const profileLink: HTMLAnchorElement | null = overlayContainerElement.querySelector('a[href="/profile"]');
    expect(profileLink?.textContent).toContain('Profile');
  });

  it('signs out and navigates to /login when "Sign out" is triggered', async () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    openAccountMenu();

    const signOutButton = Array.from(overlayContainerElement.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Sign out')
    );
    signOutButton?.click();

    httpMock.expectOne('/api/auth/logout').flush(null);
    await fixture.whenStable();

    expect(navigateSpy).toHaveBeenCalledWith('/login');
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
