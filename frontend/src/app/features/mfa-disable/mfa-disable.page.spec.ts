import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { MfaDisablePage } from './mfa-disable.page';

@Component({ template: '' })
class StubRoutePage {}

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('MfaDisablePage', () => {
  let component: MfaDisablePage;
  let fixture: ComponentFixture<MfaDisablePage>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MfaDisablePage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '', component: StubRoutePage }]),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(MfaDisablePage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('entering a 6-digit code disables MFA and navigates home on success', async () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    const req = httpMock.expectOne('/api/auth/mfa');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({ id: 1, email: 'user@example.com', emailVerified: true, mfaEnabled: false, authorities: [] });
    await flushAsync(fixture);

    expect(navigateSpy).toHaveBeenCalledWith('/');
  });

  it('shows an error and does not navigate when disabling fails', async () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const otpInput: HTMLInputElement = fixture.nativeElement.querySelector('#otp');
    otpInput.value = '123456';
    otpInput.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    httpMock.expectOne('/api/auth/mfa').flush({ error: 'Invalid code' }, { status: 401, statusText: 'Unauthorized' });
    await flushAsync(fixture);

    expect(navigateSpy).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Invalid code');
  });

  it('cancel navigates home without calling the API', async () => {
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const cancelButton: HTMLButtonElement = buttons.find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Cancel'
    ) as HTMLButtonElement;
    cancelButton.click();
    await flushAsync(fixture);

    expect(navigateSpy).toHaveBeenCalledWith('/');
  });
});
