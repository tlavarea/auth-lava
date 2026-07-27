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
    licensePlate: '6YA522',
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
    ecuSpeedMph: 62.5,
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

  it('renders the header with truck info and a link to the location on Google Maps', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('T1000');
    const subtitle: string = fixture.nativeElement.querySelector('p.uppercase').textContent.replace(/\s+/g, ' ').trim();
    expect(subtitle).toBe('Vehicle • 2023 Freightliner Cascadia');
    expect(fixture.nativeElement.textContent).toContain('Jane Trucker');
    expect(fixture.nativeElement.textContent).toContain("T231 - 53' SDL");

    const mapsLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector(
      'a[aria-label="Open in Google Maps"]'
    );
    expect(mapsLink?.getAttribute('href')).toBe('https://www.google.com/maps?q=35.221,-101.831');
  });

  it('shows the license plate in the Details section', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('License Plate');
    expect(fixture.nativeElement.textContent).toContain('6YA522');
  });

  it('shows the current speed in the header when the truck is moving', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush({ ...detail, engineState: 'On', ecuSpeedMph: 63 });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('63 MPH');
  });

  it('does not show a speed line in the header when the truck is not moving', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush({ ...detail, engineState: 'Off', ecuSpeedMph: null });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('MPH');
  });

  it('does not render a Fault Codes field', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Fault Codes');
  });

  it('shows Details and Diagnostics as accordion sections', async () => {
    httpMock.expectOne('/api/sw-expedited/trucks/truck-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const triggers: (string | undefined)[] = Array.from(
      fixture.nativeElement.querySelectorAll('hlm-accordion-trigger')
    ).map((el) => (el as Element).textContent?.trim());
    expect(triggers).toEqual(['Details', 'Diagnostics']);
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
