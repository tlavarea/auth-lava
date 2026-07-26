import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { TrailerDetailPage } from './trailer-detail/trailer-detail.page';
import { TrailerDetailResponse, TrailerListingRow } from './trailers.models';
import { TrailersPage } from './trailers.page';

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

describe('TrailersPage', () => {
  let fixture: ComponentFixture<TrailersPage>;
  let httpMock: HttpTestingController;

  const trailers: TrailerListingRow[] = [
    { id: 'trailer-1', label: "T231 - 53' SDL", manufacturer: 'Great Dane', year: 2022, currentTruckNumber: 'T1000' },
  ];

  async function createFixture(desktop: boolean): Promise<void> {
    stubMatchMedia(desktop);

    await TestBed.configureTestingModule({
      imports: [TrailersPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TrailersPage);
    fixture.detectChanges();

    httpMock.expectOne('/api/sw-expedited/trailers').flush(trailers);
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
    expect(fixture.nativeElement.querySelector('app-trailer-table')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-trailer-item')).toBeFalsy();
  });

  it('renders items on mobile', async () => {
    await createFixture(false);
    expect(fixture.nativeElement.querySelector('app-trailer-item')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-trailer-table')).toBeFalsy();
  });

  it('shows an inline master/detail split on desktop when a detail route is active', async () => {
    stubMatchMedia(true);

    const detail: TrailerDetailResponse = {
      id: 'trailer-1',
      label: "T231 - 53' SDL",
      manufacturer: 'Great Dane',
      year: 2022,
      syncedAt: '2026-07-14T00:00:00',
    };

    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(
          [{ path: '', component: TrailersPage, children: [{ path: ':id', component: TrailerDetailPage }] }],
          withComponentInputBinding()
        ),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/trailer-1', TrailersPage);

    httpMock.expectOne('/api/sw-expedited/trailers').flush(trailers);
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await harness.fixture.whenStable();
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();

    const root: HTMLElement = harness.routeNativeElement!;
    expect(root.querySelector('app-trailer-item')).toBeTruthy();
    expect(root.querySelector('app-trailer-table')).toBeFalsy();
    expect(root.querySelector('router-outlet')?.parentElement?.classList.contains('hidden')).toBe(false);
    expect(root.querySelector('[aria-current="page"]')).toBeTruthy();
  });
});
