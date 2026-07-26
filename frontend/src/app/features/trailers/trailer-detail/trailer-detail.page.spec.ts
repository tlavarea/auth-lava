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
    expect(fixture.nativeElement.textContent).toContain('More details coming soon.');
  });

  it('renders null fields as an em dash', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush({ ...detail, manufacturer: null, year: null });
    await fixture.whenStable();
    fixture.detectChanges();

    const dl: string = fixture.nativeElement.querySelector('dl').textContent;
    expect(dl).toContain('—');
  });

  it('renders a labeled mobile back link and an icon-only desktop close link', async () => {
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await fixture.whenStable();

    const backLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.lg\\:hidden');
    const closeLink: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.hidden.lg\\:inline-flex');

    expect(backLink?.textContent).toContain('Back to trailers');
    expect(closeLink?.getAttribute('aria-label')).toBe('Back to trailers');
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
