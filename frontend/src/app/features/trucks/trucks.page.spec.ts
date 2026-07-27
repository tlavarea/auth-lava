import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { TruckDetailPage } from './truck-detail/truck-detail.page';
import { TruckDetailResponse, TruckListingRow } from './trucks.models';
import { TrucksPage } from './trucks.page';

// jsdom doesn't implement matchMedia, and CDK's BreakpointObserver depends on it directly.
function stubMatchMedia(matches: boolean): void {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

describe('TrucksPage', () => {
  let fixture: ComponentFixture<TrucksPage>;
  let httpMock: HttpTestingController;

  const trucks: TruckListingRow[] = [
    {
      id: 'truck-1',
      truckNumber: 'T1000',
      engineState: 'On',
      ecuSpeedMph: null,
      currentDriverName: 'Jane Trucker',
      currentTrailerLabel: "T231 - 53' SDL",
    },
  ];

  async function createFixture(desktop: boolean): Promise<void> {
    stubMatchMedia(desktop);

    await TestBed.configureTestingModule({
      imports: [TrucksPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TrucksPage);
    fixture.detectChanges();

    httpMock.expectOne('/api/sw-expedited/trucks').flush(trucks);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', async () => {
    await createFixture(true);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the table on desktop', async () => {
    await createFixture(true);
    expect(fixture.nativeElement.querySelector('app-truck-table')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-truck-item')).toBeFalsy();
  });

  it('renders items on mobile', async () => {
    await createFixture(false);
    expect(fixture.nativeElement.querySelector('app-truck-item')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-truck-table')).toBeFalsy();
  });

  it('shows an inline master/detail split on desktop when a detail route is active', async () => {
    stubMatchMedia(true);

    const detail: TruckDetailResponse = {
      id: 'truck-1',
      truckNumber: 'T1000',
      statusCode: 1,
      vin: '1FUJA6CV12LM12345',
      licensePlate: '6YA522',
      make: 'Freightliner',
      model: 'Cascadia',
      year: 2023,
      currentDriverName: 'Jane Trucker',
      currentTrailerLabel: "T231 - 53' SDL",
      syncedAt: '2026-07-14T00:00:00',
      fuelPercent: null,
      odometerMiles: null,
      engineHours: null,
      faultCodes: null,
      engineState: null,
      ecuSpeedMph: null,
      defLevelPercent: null,
      batteryVolts: null,
      coolantTempF: null,
      engineRpm: null,
      engineLoadPercent: null,
      latitude: null,
      longitude: null,
      formattedLocation: null,
      locationTime: null,
    };

    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(
          [{ path: '', component: TrucksPage, children: [{ path: ':id', component: TruckDetailPage }] }],
          withComponentInputBinding()
        ),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/truck-1', TrucksPage);

    httpMock.expectOne('/api/sw-expedited/trucks').flush(trucks);
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/route-history').flush({ points: [], stops: [] });
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1/safety-events').flush([]);
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();

    const root: HTMLElement = harness.routeNativeElement!;
    expect(root.querySelector('app-truck-item')).toBeTruthy();
    expect(root.querySelector('app-truck-table')).toBeFalsy();
    expect(root.querySelector('router-outlet')?.parentElement?.classList.contains('hidden')).toBe(false);
    expect(root.querySelector('[aria-current="page"]')).toBeTruthy();
  });
});
