import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthStore } from '@core/auth/auth.store';
import { DashboardPage } from './dashboard.page';

describe('DashboardPage', () => {
  let component: DashboardPage;
  let fixture: ComponentFixture<DashboardPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(DashboardPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('greets the signed-in user by email', async () => {
    const authStore = TestBed.inject(AuthStore);
    const bootstrapPromise = authStore.bootstrap();
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: 1, email: 'a@b.com', emailVerified: true, mfaEnabled: false, authorities: [] });
    await bootstrapPromise;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('a@b.com');
  });
});
