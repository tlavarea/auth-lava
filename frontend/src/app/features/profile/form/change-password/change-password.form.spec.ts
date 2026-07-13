import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChangePasswordForm } from './change-password.form';

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

describe('ChangePasswordForm', () => {
  let component: ChangePasswordForm;
  let fixture: ComponentFixture<ChangePasswordForm>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChangePasswordForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(ChangePasswordForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
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
});
