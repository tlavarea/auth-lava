import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';

import { MfaDisableDialog } from './mfa-disable.dialog';

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

function clickButtonWithText(fixture: ComponentFixture<unknown>, text: string): void {
  const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
  const button: HTMLButtonElement = buttons.find(
    (candidate: HTMLButtonElement) => candidate.textContent?.trim() === text
  ) as HTMLButtonElement;
  button.click();
}

describe('MfaDisableDialog', () => {
  let component: MfaDisableDialog;
  let fixture: ComponentFixture<MfaDisableDialog>;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [MfaDisableDialog],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: BrnDialogRef, useValue: dialogRef }],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(MfaDisableDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('confirm step', () => {
    it('shows the confirmation title and description', () => {
      expect(fixture.nativeElement.textContent).toContain('Are you sure?');
      expect(fixture.nativeElement.textContent).toContain(
        'This will remove the extra layer of security to your account. You will now only use your password to log in.'
      );
    });

    it('"Not now" closes the dialog without calling the API', () => {
      clickButtonWithText(fixture, 'Not now');

      expect(dialogRef.close).toHaveBeenCalledWith();
    });

    it('"Turn it off" advances to the apply step', () => {
      clickButtonWithText(fixture, 'Turn it off');
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('#otp')).toBeTruthy();
      expect(fixture.nativeElement.textContent).toContain('Disable two-factor authentication');
    });
  });

  describe('apply step', () => {
    beforeEach(() => {
      clickButtonWithText(fixture, 'Turn it off');
      fixture.detectChanges();
    });

    it('entering a 6-digit code disables MFA and closes the dialog on success', async () => {
      const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
      otpInput.value = '123456';
      otpInput.dispatchEvent(new Event('input'));
      await fixture.whenStable();

      const req = httpMock.expectOne('/api/auth/mfa');
      expect(req.request.method).toBe('DELETE');
      expect(req.request.body).toEqual({ code: '123456' });
      req.flush({ id: 1, email: 'user@example.com', emailVerified: true, mfaEnabled: false, authorities: [] });
      await flushAsync(fixture);

      expect(dialogRef.close).toHaveBeenCalledWith();
    });

    it('shows an error and does not close the dialog when disabling fails', async () => {
      const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
      otpInput.value = '123456';
      otpInput.dispatchEvent(new Event('input'));
      await fixture.whenStable();

      httpMock.expectOne('/api/auth/mfa').flush({ error: 'Invalid code' }, { status: 401, statusText: 'Unauthorized' });
      await flushAsync(fixture);

      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(fixture.nativeElement.textContent).toContain('Invalid code');
    });

    it('"Cancel" closes the dialog without calling the API', () => {
      clickButtonWithText(fixture, 'Cancel');

      expect(dialogRef.close).toHaveBeenCalledWith();
    });
  });
});
