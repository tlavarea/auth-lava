import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ShipmentListingRow } from './shipments.models';
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

  it('keeps the detail sheet off-screen, inert, and its backdrop absent when no detail route is active', async () => {
    await createFixture(true);

    expect(fixture.nativeElement.querySelector('.translate-x-full')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[inert]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[class*="bg-black/50"]')).toBeFalsy();
  });
});
