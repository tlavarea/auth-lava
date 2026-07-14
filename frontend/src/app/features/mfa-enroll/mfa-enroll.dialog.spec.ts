import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';

import { MfaEnrollDialog } from './mfa-enroll.dialog';

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('MfaEnrollDialog', () => {
  let component: MfaEnrollDialog;
  let fixture: ComponentFixture<MfaEnrollDialog>;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [MfaEnrollDialog],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: BrnDialogRef, useValue: dialogRef }],
    }).compileComponents();

    fixture = TestBed.createComponent(MfaEnrollDialog);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create and kick off enrollment', async () => {
    httpMock
      .expectOne('/api/auth/mfa/enroll')
      .flush({ mfaMethodId: '1', secret: 'secret', otpAuthUri: 'otpauth://', qrCodeDataUri: 'data:image/png;x' });
    await fixture.whenStable();

    expect(component).toBeTruthy();
  });

  it('entering a 6-digit code verifies enrollment and shows backup codes', async () => {
    httpMock
      .expectOne('/api/auth/mfa/enroll')
      .flush({ mfaMethodId: '1', secret: 'secret', otpAuthUri: 'otpauth://', qrCodeDataUri: 'data:image/png;x' });
    await fixture.whenStable();

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const verifyReq = httpMock.expectOne('/api/auth/mfa/enroll/verify');
    expect(verifyReq.request.body).toEqual({ mfaMethodId: '1', code: '123456' });
    verifyReq.flush({
      backupCodes: ['aaaa-1111', 'bbbb-2222'],
      user: { id: 1, email: 'user@example.com', emailVerified: true, mfaEnabled: true, authorities: [] },
    });
    await flushAsync(fixture);

    expect(fixture.nativeElement.querySelector('#otp')).toBeFalsy();
    expect(fixture.nativeElement.textContent).toContain('aaaa-1111');
    expect(fixture.nativeElement.textContent).toContain('bbbb-2222');
  });

  it('shows an error and stays on the verify step when verification fails', async () => {
    httpMock
      .expectOne('/api/auth/mfa/enroll')
      .flush({ mfaMethodId: '1', secret: 'secret', otpAuthUri: 'otpauth://', qrCodeDataUri: 'data:image/png;x' });
    await fixture.whenStable();

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    httpMock
      .expectOne('/api/auth/mfa/enroll/verify')
      .flush({ error: 'Invalid code' }, { status: 400, statusText: 'Bad Request' });
    await flushAsync(fixture);

    expect(fixture.nativeElement.querySelector('#otp')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Invalid code');
  });

  it('clicking "Continue" after saving backup codes closes the dialog', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true,
    });

    httpMock
      .expectOne('/api/auth/mfa/enroll')
      .flush({ mfaMethodId: '1', secret: 'secret', otpAuthUri: 'otpauth://', qrCodeDataUri: 'data:image/png;x' });
    await fixture.whenStable();

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    httpMock.expectOne('/api/auth/mfa/enroll/verify').flush({
      backupCodes: ['aaaa-1111', 'bbbb-2222'],
      user: { id: 1, email: 'user@example.com', emailVerified: true, mfaEnabled: true, authorities: [] },
    });
    await flushAsync(fixture);

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const copyButton: HTMLButtonElement = buttons.find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Copy all'
    ) as HTMLButtonElement;
    copyButton.click();
    await flushAsync(fixture);

    const buttonsAfterCopy: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const continueButton: HTMLButtonElement = buttonsAfterCopy.find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Continue'
    ) as HTMLButtonElement;
    continueButton.click();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
