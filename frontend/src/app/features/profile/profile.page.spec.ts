import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthStore } from '@core/auth/auth.store';
import { ProfilePage } from './profile.page';

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

function submitNewEmail(fixture: ComponentFixture<unknown>, newEmail: string): void {
  const newEmailInput: HTMLInputElement = fixture.nativeElement.querySelector('#newEmail');
  newEmailInput.value = newEmail;
  newEmailInput.dispatchEvent(new Event('input'));
  newEmailInput.closest('form')?.dispatchEvent(new Event('submit', { cancelable: true }));
}

describe('ProfilePage', () => {
  let component: ProfilePage;
  let fixture: ComponentFixture<ProfilePage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfilePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(ProfilePage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the email and a verified badge, and the enroll link when not MFA-enrolled', async () => {
    const authStore = TestBed.inject(AuthStore);
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: true, mfaEnabled: false, authorities: [] });
    await bootstrapPromise;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('a@b.com');
    expect(fixture.nativeElement.textContent).toContain('Verified');

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/mfa/enroll"]');
    expect(link.textContent).toContain('Set up two-factor authentication');
    expect(fixture.nativeElement.querySelector('a[href="/mfa/disable"]')).toBeNull();
  });

  it('shows an unverified badge and the disable link when MFA-enabled but email unverified', async () => {
    const authStore = TestBed.inject(AuthStore);
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: false, mfaEnabled: true, authorities: [] });
    await bootstrapPromise;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unverified');

    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/mfa/disable"]');
    expect(link.textContent).toContain('Disable two-factor authentication');
    expect(fixture.nativeElement.querySelector('a[href="/mfa/enroll"]')).toBeNull();
  });

  // The form behavior (validation, submission, server errors) is covered by
  // ChangeEmailForm/ChangePasswordForm's own specs. This verifies that ProfilePage
  // correctly wires the change-email form's outputs into its own card description.
  it('updates the change-email card description as the child form moves between steps', async () => {
    expect(fixture.nativeElement.textContent).toContain("We'll send a verification code to your new address.");

    submitNewEmail(fixture, 'new@example.com');
    await flushAsync(fixture);
    httpMock.expectOne('/api/auth/email/change').flush(null);
    await flushAsync(fixture);

    expect(fixture.nativeElement.textContent).toContain('Enter the code we sent to new@example.com.');
  });
});
