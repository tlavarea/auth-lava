import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { MfaVerifyPage } from './mfa-verify.page';

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('MfaVerifyPage', () => {
  let component: MfaVerifyPage;
  let fixture: ComponentFixture<MfaVerifyPage>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MfaVerifyPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MfaVerifyPage);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('entering a 6-digit code verifies it and navigates home on success', async () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const req = httpMock.expectOne('/api/auth/mfa/verify');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({ id: '1', email: 'user@example.com' });
    await flushAsync(fixture);

    expect(navigateSpy).toHaveBeenCalledWith('/');
  });

  it('shows an error and does not navigate when verification fails', async () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    httpMock
      .expectOne('/api/auth/mfa/verify')
      .flush({ error: 'Invalid code' }, { status: 400, statusText: 'Bad Request' });
    await flushAsync(fixture);

    expect(navigateSpy).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Invalid code');
  });
});
