import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TrailerDetailResponse, TrailerListingRow } from './trailers.models';
import { TrailersStore } from './trailers.store';

describe('TrailersStore', () => {
  let store: InstanceType<typeof TrailersStore>;
  let httpMock: HttpTestingController;

  const listing: TrailerListingRow = {
    id: 'trailer-1',
    label: "T231 - 53' SDL",
    manufacturer: 'Great Dane',
    year: 2022,
    currentTruckNumber: 'T1000',
  };

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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TrailersStore],
    });
    store = TestBed.inject(TrailersStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts empty and idle', () => {
    expect(store.trailers()).toEqual([]);
    expect(store.listStatus()).toBe('idle');
    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });

  it('loadTrailers() populates the list on success', async () => {
    const loadPromise = store.loadTrailers();
    httpMock.expectOne('/api/sw-expedited/trailers').flush([listing]);
    await loadPromise;

    expect(store.trailers()).toEqual([listing]);
    expect(store.listStatus()).toBe('idle');
  });

  it('loadTrailers() marks the list status as error on failure', async () => {
    const loadPromise = store.loadTrailers();
    httpMock.expectOne('/api/sw-expedited/trailers').flush(null, { status: 500, statusText: 'Server Error' });
    await loadPromise;

    expect(store.listStatus()).toBe('error');
  });

  it('loadTrailerDetail() populates the selected detail on success', async () => {
    const loadPromise = store.loadTrailerDetail('trailer-1');
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await loadPromise;

    expect(store.selectedDetail()).toEqual(detail);
    expect(store.detailStatus()).toBe('idle');
  });

  it('loadTrailerDetail() marks the detail status as error on failure', async () => {
    const loadPromise = store.loadTrailerDetail('trailer-1');
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(null, { status: 404, statusText: 'Not Found' });
    await loadPromise;

    expect(store.detailStatus()).toBe('error');
  });

  it('clearSelectedDetail() resets the selected detail', async () => {
    const loadPromise = store.loadTrailerDetail('trailer-1');
    httpMock.expectOne('/api/sw-expedited/trailers/trailer-1').flush(detail);
    await loadPromise;

    store.clearSelectedDetail();

    expect(store.selectedDetail()).toBeNull();
    expect(store.detailStatus()).toBe('idle');
  });
});
