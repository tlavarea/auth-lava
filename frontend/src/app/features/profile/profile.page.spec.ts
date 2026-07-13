import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HlmDialogService } from '@spartan-ng/helm/dialog';

import { AuthStore } from '@core/auth/auth.store';
import { MfaDisableDialog } from '@features/mfa-disable/mfa-disable.dialog';
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

  it('shows the email and a verified badge', async () => {
    const authStore = TestBed.inject(AuthStore);
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: true, mfaEnabled: false, authorities: [] });
    await bootstrapPromise;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('a@b.com');
    expect(fixture.nativeElement.textContent).toContain('Verified');
  });

  it('shows an unverified badge when the email is not verified', async () => {
    const authStore = TestBed.inject(AuthStore);
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: false, mfaEnabled: true, authorities: [] });
    await bootstrapPromise;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Unverified');
  });

  it('opens the MFA-disable dialog when Update is clicked while MFA is enabled', async () => {
    const authStore = TestBed.inject(AuthStore);
    const dialogService = TestBed.inject(HlmDialogService);
    const openSpy = vi.spyOn(dialogService, 'open');
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: true, mfaEnabled: true, authorities: [] });
    await bootstrapPromise;
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const updateButton: HTMLButtonElement = buttons.find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Update'
    ) as HTMLButtonElement;
    updateButton.click();

    expect(openSpy).toHaveBeenCalledWith(MfaDisableDialog, { showCloseButton: false });
  });

  it('does nothing when Update is clicked while MFA is disabled', async () => {
    const authStore = TestBed.inject(AuthStore);
    const dialogService = TestBed.inject(HlmDialogService);
    const openSpy = vi.spyOn(dialogService, 'open');
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: true, mfaEnabled: false, authorities: [] });
    await bootstrapPromise;
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const updateButton: HTMLButtonElement = buttons.find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Update'
    ) as HTMLButtonElement;
    updateButton.click();

    expect(openSpy).not.toHaveBeenCalled();
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
