import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MfaEnrollPage } from './mfa-enroll.page';

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
});
