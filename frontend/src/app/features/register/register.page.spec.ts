import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { RegisterPage } from './register.page';

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('RegisterPage', () => {
  let component: RegisterPage;
  let fixture: ComponentFixture<RegisterPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create and start on the email step', () => {
    expect(component).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#email')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#otp')).toBeFalsy();
  });

  it('submitting a valid email calls register/start and advances to the code step', async () => {
    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector('#email');
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    form.dispatchEvent(new Event('submit', { cancelable: true }));

    const req = httpMock.expectOne('/api/auth/register/start');
    expect(req.request.body).toEqual({ email: 'new@example.com' });
    req.flush(null);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('#otp')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('new@example.com');
  });

  it('resend is disabled immediately after entering the code step', async () => {
    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector('#email');
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    httpMock.expectOne('/api/auth/register/start').flush(null);
    await fixture.whenStable();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const resendButton = buttons.find((button) => button.textContent?.includes('Resend'));
    expect(resendButton?.disabled).toBe(true);
  });

  it('entering a 6-digit code verifies it and advances to the password step', async () => {
    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector('#email');
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    httpMock.expectOne('/api/auth/register/start').flush(null);
    await fixture.whenStable();

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const verifyReq = httpMock.expectOne('/api/auth/register/verify-code');
    expect(verifyReq.request.body).toEqual({ email: 'new@example.com', code: '123456' });
    verifyReq.flush({ registrationToken: 'token-123' });
    await flushAsync(fixture);

    expect(fixture.nativeElement.querySelector('#password')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#otp')).toBeFalsy();
  });

  it('shows an error and stays on the code step when verification fails', async () => {
    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector('#email');
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { cancelable: true }));
    httpMock.expectOne('/api/auth/register/start').flush(null);
    await fixture.whenStable();

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    httpMock
      .expectOne('/api/auth/register/verify-code')
      .flush({ error: 'Invalid code' }, { status: 400, statusText: 'Bad Request' });
    await flushAsync(fixture);

    expect(fixture.nativeElement.querySelector('#otp')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Invalid code');
  });
});
