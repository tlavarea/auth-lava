import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MfaEnrollPage } from './mfa-enroll.page';

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('MfaEnrollPage', () => {
  let component: MfaEnrollPage;
  let fixture: ComponentFixture<MfaEnrollPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MfaEnrollPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MfaEnrollPage);
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
    verifyReq.flush({ backupCodes: ['aaaa-1111', 'bbbb-2222'] });
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
});
