import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AuthStore } from '@core/auth/auth.store';
import { guestGuard } from '@core/guards/guest-guard';
import { MfaVerifyPage } from './mfa-verify.page';

@Component({ template: '' })
class StubRoutePage {}

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
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'login', canActivate: [guestGuard], component: StubRoutePage },
          { path: 'mfa/verify', component: StubRoutePage },
        ]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);

    // The mfa-verify route is only ever reached via mfaPendingGuard, i.e. once AuthStore is
    // already in the 'mfa-pending' state - reproduce that here so guestGuard (on /login) behaves
    // as it would for real when the component later navigates away.
    const authStore = TestBed.inject(AuthStore);
    const bootstrapPromise = authStore.bootstrap();
    httpMock.expectOne('/api/auth/me').flush(null, { status: 403, statusText: 'Forbidden' });
    await bootstrapPromise;

    fixture = TestBed.createComponent(MfaVerifyPage);
    component = fixture.componentInstance;
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

  it('logs out and redirects to login after too many failed attempts', async () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    httpMock
      .expectOne('/api/auth/mfa/verify')
      .flush({ error: 'Too many failed attempts - try again later' }, { status: 429, statusText: 'Too Many Requests' });
    await flushAsync(fixture);

    expect(navigateSpy).not.toHaveBeenCalled();

    await new Promise((resolve) => setTimeout(resolve, 2100));

    httpMock.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await flushAsync(fixture);

    expect(navigateSpy).toHaveBeenCalledWith('/login');
    // guestGuard redirects mfa-pending sessions away from /login back to /mfa/verify - asserting
    // on the resolved URL (not just the navigateByUrl call) catches that race regression.
    expect(router.url).toBe('/login');
  }, 10000);
});
