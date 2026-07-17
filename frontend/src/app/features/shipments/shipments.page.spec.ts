import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { ShipmentDetailPage } from './shipment-detail/shipment-detail.page';
import { ShipmentDetailResponse, ShipmentListingRow } from './shipments.models';
import { ShipmentsPage } from './shipments.page';

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

describe('ShipmentsPage', () => {
  let fixture: ComponentFixture<ShipmentsPage>;
  let httpMock: HttpTestingController;

  const shipments: ShipmentListingRow[] = [
    {
      offerId: 42,
      status: 'ACCEPTED',
      expirationDate: null,
      shipmentId: 'SHP-42',
      shipmentType: 'HHG',
      rank: 'E-5',
      gbloc: 'ABCD',
      origin: 'Fort Liberty, NC',
      destination: 'Joint Base Lewis-McChord, WA',
      equipType: '53ft Van',
      conveyancesOffered: 1,
      conveyancesAccepted: 1,
      pickupDate: '2026-08-01',
      requiredDeliveryDate: '2026-08-10',
      syncedAt: '2026-07-14T00:00:00',
      viablePickup: false,
    },
  ];

  async function createFixture(desktop: boolean): Promise<void> {
    stubMatchMedia(desktop);

    await TestBed.configureTestingModule({
      imports: [ShipmentsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ShipmentsPage);
    fixture.detectChanges();

    httpMock.expectOne('/api/sw-expedited/shipments').flush(shipments);
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
    expect(fixture.nativeElement.querySelector('app-shipment-table')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-shipment-item')).toBeFalsy();
  });

  it('renders items on mobile', async () => {
    await createFixture(false);
    expect(fixture.nativeElement.querySelector('app-shipment-item')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-shipment-table')).toBeFalsy();
  });

  it('keeps the mobile detail sheet off-screen, inert, with no backdrop when no detail route is active', async () => {
    await createFixture(false);

    expect(fixture.nativeElement.querySelector('.translate-x-full')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[inert]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[class*="bg-black/50"]')).toBeFalsy();
  });

  it('collapses the detail pane out of layout on desktop when no detail route is active', async () => {
    await createFixture(true);

    expect(fixture.nativeElement.querySelector('.hidden')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[class*="bg-black/50"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('app-shipment-table')).toBeTruthy();
  });

  it('shows an inline master/detail split on desktop when a detail route is active', async () => {
    stubMatchMedia(true);

    const detail: ShipmentDetailResponse = {
      listing: shipments[0],
      totalAmount: 1000,
      lineHaulCost: 900,
      rateUsed: 1.5,
      scac: 'ABCD',
      scacName: 'Acme Carrier',
      tenderNumber: 'T-1',
      equipmentDesc: '53ft Van',
      requestorName: 'Jane Doe',
      requestorEmail: 'jane@example.com',
      rawResponse: '{}',
      syncedAt: '2026-07-14T00:00:00',
      bidDetail: null,
    };

    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(
          [{ path: '', component: ShipmentsPage, children: [{ path: ':id', component: ShipmentDetailPage }] }],
          withComponentInputBinding()
        ),
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/42', ShipmentsPage);

    httpMock.expectOne('/api/sw-expedited/shipments').flush(shipments);
    httpMock.expectOne('/api/sw-expedited/shipments/42').flush(detail);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();

    const root: HTMLElement = harness.routeNativeElement!;
    expect(root.querySelector('app-shipment-item')).toBeTruthy();
    expect(root.querySelector('app-shipment-table')).toBeFalsy();
    expect(root.querySelector('router-outlet')?.parentElement?.classList.contains('hidden')).toBe(false);
    expect(root.querySelector('[class*="bg-black/50"]')).toBeFalsy();
    expect(root.querySelector('[aria-current="page"]')).toBeTruthy();
  });
});
