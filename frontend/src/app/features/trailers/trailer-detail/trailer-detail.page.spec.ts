import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TrailerDetailResponse } from '../trailers.models';
import { TrailersStore } from '../trailers.store';
import { TrailerDetailPage } from './trailer-detail.page';

describe('TrailerDetailPage', () => {
  let fixture: ComponentFixture<TrailerDetailPage>;
  let httpMock: HttpTestingController;

  const detail: TrailerDetailResponse = {
    id: 'trailer-1',
    label: "T231 - 53' SDL",
    manufacturer: 'Great Dane',
    year: 2022,
    vin: '5MC125315H5165489',
    licensePlate: '34A1W4',
    assetSerialNumber: '5MC125315H5165489',
    currentTruckNumber: 'T1000',
    currentDriverName: 'Jane Trucker',
    syncedAt: '2026-07-14T00:00:00',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TrailerDetailPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), TrailersStore],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TrailerDetailPage);
    fixture.componentRef.setInput('id', 'trailer-1');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders the trailer detail for the routed :id', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("T231 - 53' SDL");
    expect(fixture.nativeElement.textContent).toContain('Great Dane');
    expect(fixture.nativeElement.textContent).toContain('2022');
    expect(fixture.nativeElement.textContent).toContain('5MC125315H5165489');
    expect(fixture.nativeElement.textContent).toContain('34A1W4');
    expect(fixture.nativeElement.textContent).toContain('T1000');
    expect(fixture.nativeElement.textContent).toContain('Jane Trucker');
  });

  it('renders null fields as an em dash', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush({
      ...detail,
      manufacturer: null,
      year: null,
      vin: null,
      licensePlate: null,
      assetSerialNumber: null,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const dl: string = fixture.nativeElement.querySelector('dl').textContent;
    expect(dl).toContain('—');
  });

  it('renders the header with the trailer label and subtitle', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain("T231 - 53' SDL");
    const subtitle: string = fixture.nativeElement.querySelector('p.uppercase').textContent.replace(/\s+/g, ' ').trim();
    expect(subtitle).toBe('Trailer • 2022 Great Dane');
  });

  it('shows a fallback message when no truck or driver is assigned', async () => {
    httpMock
      .expectOne('/api/sw-expedited/trailers/trailer-1')
      .flush({ ...detail, currentTruckNumber: null, currentDriverName: null });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No truck assigned');
    expect(fixture.nativeElement.textContent).toContain('No driver assigned');
  });

  it('shows Details as an accordion section', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const triggers: (string | undefined)[] = Array.from(
      fixture.nativeElement.querySelectorAll('hlm-accordion-trigger')
    ).map((el) => (el as Element).textContent?.trim());
    expect(triggers).toEqual(['Details']);
  });

  it('renders a close button that routes back to the trailer list', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    const closeLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[aria-label="Back to trailers"]');
    expect(closeLink).not.toBeNull();
  });

  it('does not render a map', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-truck-route-map')).toBeNull();
  });

  it('shows an error message when the load fails', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Couldn't load this trailer.");
  });

  it('clears the selected detail on destroy', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await fixture.whenStable();

    const store = TestBed.inject(TrailersStore);
    expect(store.selectedDetail()).toEqual(detail);

    fixture.destroy();

    expect(store.selectedDetail()).toBeNull();
  });
});
