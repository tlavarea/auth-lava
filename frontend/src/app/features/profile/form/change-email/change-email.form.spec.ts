import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmailChangeStep } from '@models/models';
import { ChangeEmailForm } from './change-email.form';

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

describe('ChangeEmailForm', () => {
  let component: ChangeEmailForm;
  let fixture: ComponentFixture<ChangeEmailForm>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChangeEmailForm],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(ChangeEmailForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('starts an email change, emits the pending address and step, and moves to the code step', async () => {
    const steps: EmailChangeStep[] = [];
    const pendingEmails: string[] = [];
    component.stepChange.subscribe((step: EmailChangeStep): number => steps.push(step));
    component.pendingNewEmailChange.subscribe((email: string): number => pendingEmails.push(email));

    submitNewEmail(fixture, 'new@example.com');
    await flushAsync(fixture);

    const req = httpMock.expectOne('/api/auth/email/change');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newEmail: 'new@example.com' });
    req.flush(null);
    await flushAsync(fixture);

    expect(pendingEmails).toEqual(['new@example.com']);
    expect(steps).toEqual(['code']);
    expect(fixture.nativeElement.querySelector('#emailChangeCode')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#newEmail')).toBeNull();
  });

  it('verifying the correct code shows success, emits the email step, and clears the code input', async () => {
    const steps: EmailChangeStep[] = [];
    component.stepChange.subscribe((step: EmailChangeStep): number => steps.push(step));

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
    expect(steps).toEqual(['code', 'email']);
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

  it('"use a different email" cancels back to the email step, emits the step, and does not call the API', async () => {
    const steps: EmailChangeStep[] = [];
    component.stepChange.subscribe((step: EmailChangeStep): number => steps.push(step));

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
    expect(steps).toEqual(['code', 'email']);
  });
});
