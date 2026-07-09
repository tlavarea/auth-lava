import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MfaVerifyPage } from './mfa-verify.page';

describe('MfaVerifyPage', () => {
  let component: MfaVerifyPage;
  let fixture: ComponentFixture<MfaVerifyPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MfaVerifyPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MfaVerifyPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
