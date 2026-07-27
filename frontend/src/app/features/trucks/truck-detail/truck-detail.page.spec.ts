import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TruckDetailResponse } from '../trucks.models';
import { TrucksStore } from '../trucks.store';
import { TruckDetailPage } from './truck-detail.page';

describe('TruckDetailPage', () => {
  let fixture: ComponentFixture<TruckDetailPage>;
  let httpMock: HttpTestingController;

  const detail: TruckDetailResponse = {
    id: 'truck-1',
    truckNumber: 'T1000',
    statusCode: 1,
    vin: '1FUJA6CV12LM12345',
    make: 'Freightliner',
    model: 'Cascadia',
    year: 2023,
    currentDriverName: 'Jane Trucker',
    currentTrailerLabel: "T231 - 53' SDL",
    syncedAt: '2026-07-14T00:00:00',
    fuelPercent: 62,
    odometerMiles: 184203,
    engineHours: 5412,
    faultCodes: null,
    engineState: 'On',
    defLevelPercent: 41,
    batteryVolts: 13.2,
    coolantTempF: 198,
    engineRpm: 1200,
    engineLoadPercent: 54,
    latitude: 35.221,
    longitude: -101.831,
    formattedLocation: 'I-40 near Amarillo, TX',
    locationTime: '2026-07-14T00:00:00',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TruckDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), TrucksStore],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TruckDetailPage);
    fixture.componentRef.setInput('id', 'truck-1');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders the truck detail for the routed :id', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('T1000');
    expect(fixture.nativeElement.textContent).toContain('1FUJA6CV12LM12345');
    expect(fixture.nativeElement.textContent).toContain('Jane Trucker');
    expect(fixture.nativeElement.textContent).toContain("T231 - 53' SDL");
    expect(fixture.nativeElement.textContent).toContain('I-40 near Amarillo, TX');
  });

  it('shows a fallback message when no Samsara diagnostics are available', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush({
      ...detail,
      fuelPercent: null,
      odometerMiles: null,
      engineHours: null,
      engineState: null,
      formattedLocation: null,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No Samsara diagnostics available for this truck.');
  });

  it('renders null fields as an em dash', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush({ ...detail, vin: null });
    await fixture.whenStable();
    fixture.detectChanges();

    const dl: string = fixture.nativeElement.querySelector('dl').textContent;
    expect(dl).toContain('—');
  });

  it('renders a labeled mobile back link and an icon-only desktop close link', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await fixture.whenStable();

    const backLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.lg\\:hidden');
    const closeLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.hidden.lg\\:inline-flex');

    expect(backLink?.textContent).toContain('Back to trucks');
    expect(closeLink?.getAttribute('aria-label')).toBe('Back to trucks');
  });

  it('shows an error message when the load fails', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Couldn't load this truck.");
  });

  it('clears the selected detail on destroy', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await fixture.whenStable();

    const store = TestBed.inject(TrucksStore);
    expect(store.selectedDetail()).toEqual(detail);

    fixture.destroy();

    expect(store.selectedDetail()).toBeNull();
  });
});
