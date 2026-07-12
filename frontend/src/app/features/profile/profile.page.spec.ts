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

function fillPasswordForm(
  fixture: ComponentFixture<unknown>,
  values: { currentPassword: string; newPassword: string; confirmNewPassword: string }
): void {
  const currentPasswordInput: HTMLInputElement = fixture.nativeElement.querySelector('#currentPassword');
  currentPasswordInput.value = values.currentPassword;
  currentPasswordInput.dispatchEvent(new Event('input'));

  const newPasswordInput: HTMLInputElement = fixture.nativeElement.querySelector('#newPassword');
  newPasswordInput.value = values.newPassword;
  newPasswordInput.dispatchEvent(new Event('input'));

  const confirmNewPasswordInput: HTMLInputElement = fixture.nativeElement.querySelector('#confirmNewPassword');
  confirmNewPasswordInput.value = values.confirmNewPassword;
  confirmNewPasswordInput.dispatchEvent(new Event('input'));
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

  it('changes the password, shows a success message, and clears the form', async () => {
    fillPasswordForm(fixture, {
      currentPassword: 'old-password',
      newPassword: 'new-password',
      confirmNewPassword: 'new-password',
    });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await flushAsync(fixture);

    const req = httpMock.expectOne('/api/auth/password');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ currentPassword: 'old-password', newPassword: 'new-password' });
    req.flush(null);
    await flushAsync(fixture);

    expect(fixture.nativeElement.textContent).toContain('Password changed successfully');
    expect(fixture.nativeElement.querySelector('#currentPassword').value).toBe('');
    expect(fixture.nativeElement.querySelector('#newPassword').value).toBe('');
    // Clearing the model without resetting touched/dirty state would make the now-empty
    // required fields immediately show their own validation errors alongside the success message.
    expect(fixture.nativeElement.textContent).not.toContain('Enter your current password');
    expect(fixture.nativeElement.textContent).not.toContain('Enter a new password');
    expect(fixture.nativeElement.textContent).not.toContain('Confirm your new password');
  });

  it('shows a client-side error and does not call the API when the passwords do not match', async () => {
    fillPasswordForm(fixture, {
      currentPassword: 'old-password',
      newPassword: 'new-password',
      confirmNewPassword: 'different-password',
    });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await flushAsync(fixture);

    expect(fixture.nativeElement.textContent).toContain('Passwords do not match');
    httpMock.expectNone('/api/auth/password');
  });

  it('shows a server error and clears only the current password field on failure', async () => {
    fillPasswordForm(fixture, {
      currentPassword: 'wrong-password',
      newPassword: 'new-password',
      confirmNewPassword: 'new-password',
    });
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    await flushAsync(fixture);

    httpMock
      .expectOne('/api/auth/password')
      .flush({ error: 'Current password is incorrect' }, { status: 401, statusText: 'Unauthorized' });
    await flushAsync(fixture);

    expect(fixture.nativeElement.textContent).toContain('Current password is incorrect');
    expect(fixture.nativeElement.querySelector('#currentPassword').value).toBe('');
    expect(fixture.nativeElement.querySelector('#newPassword').value).toBe('new-password');
  });

  it('starts an email change and moves to the code step showing the pending address', async () => {
    submitNewEmail(fixture, 'new@example.com');
    await flushAsync(fixture);

    const req = httpMock.expectOne('/api/auth/email/change');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newEmail: 'new@example.com' });
    req.flush(null);
    await flushAsync(fixture);

    expect(fixture.nativeElement.textContent).toContain('new@example.com');
    expect(fixture.nativeElement.querySelector('#emailChangeCode')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#newEmail')).toBeNull();
  });

  it('verifying the correct code shows a success message and returns to the email step', async () => {
    submitNewEmail(fixture, 'new@example.com');
    await flushAsync(fixture);
    httpMock.expectOne('/api/auth/email/change').flush(null);
    await flushAsync(fixture);

    const codeInput: HTMLInputElement = fixture.nativeElement.querySelector('#emailChangeCode');
    codeInput.value = '123456';
    codeInput.dispatchEvent(new Event('input'));
    await flushAsync(fixture);

    const req = httpMock.expectOne('/api/auth/email/change/verify');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({ id: 1, email: 'new@example.com', emailVerified: true, mfaEnabled: false, authorities: [] });
    await flushAsync(fixture);

    expect(fixture.nativeElement.textContent).toContain('Email changed successfully');
    expect(fixture.nativeElement.querySelector('#newEmail')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#emailChangeCode')).toBeNull();
  });

  it('shows a server error and stays on the code step when the code is wrong', async () => {
    submitNewEmail(fixture, 'new@example.com');
    await flushAsync(fixture);
    httpMock.expectOne('/api/auth/email/change').flush(null);
    await flushAsync(fixture);

    const codeInput: HTMLInputElement = fixture.nativeElement.querySelector('#emailChangeCode');
    codeInput.value = '000000';
    codeInput.dispatchEvent(new Event('input'));
    await flushAsync(fixture);

    httpMock
      .expectOne('/api/auth/email/change/verify')
      .flush({ error: 'Invalid verification code' }, { status: 401, statusText: 'Unauthorized' });
    await flushAsync(fixture);

    expect(fixture.nativeElement.textContent).toContain('Invalid verification code');
    expect(fixture.nativeElement.querySelector('#emailChangeCode')).toBeTruthy();
  });

  it('resend code re-calls the start endpoint with the same pending address', async () => {
    submitNewEmail(fixture, 'new@example.com');
    await flushAsync(fixture);
    httpMock.expectOne('/api/auth/email/change').flush(null);
    await flushAsync(fixture);

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const resendButton: HTMLButtonElement = buttons.find((button) =>
      button.textContent?.includes('Resend code')
    ) as HTMLButtonElement;
    resendButton.click();
    await flushAsync(fixture);

    const req = httpMock.expectOne('/api/auth/email/change');
    expect(req.request.body).toEqual({ newEmail: 'new@example.com' });
    req.flush(null);
  });

  it('"use a different email" cancels back to the email step without calling the API', async () => {
    submitNewEmail(fixture, 'new@example.com');
    await flushAsync(fixture);
    httpMock.expectOne('/api/auth/email/change').flush(null);
    await flushAsync(fixture);

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const cancelButton: HTMLButtonElement = buttons.find((button) =>
      button.textContent?.includes('Use a different email')
    ) as HTMLButtonElement;
    cancelButton.click();
    await flushAsync(fixture);

    expect(fixture.nativeElement.querySelector('#newEmail')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#emailChangeCode')).toBeNull();
    httpMock.expectNone('/api/auth/email/change/verify');
  });
});
