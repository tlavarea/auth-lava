import { TrailerListingRow } from '../trailers.models';
import { filterTrailers, paginateTrailers, sortTrailers } from './trailer-table.filters';

const trailers: TrailerListingRow[] = [
  { id: 't1', label: 'T231 - Zed', manufacturer: 'Great Dane', year: 2022, currentTruckNumber: null },
  { id: 't2', label: 'T100 - Amber', manufacturer: 'Wabash', year: 2020, currentTruckNumber: null },
];

describe('filterTrailers', () => {
  it('matches by label or manufacturer, case-insensitively', () => {
    expect(filterTrailers(trailers, { searchText: 'zed' })).toEqual([trailers[0]]);
    expect(filterTrailers(trailers, { searchText: 'wabash' })).toEqual([trailers[1]]);
  });

  it('returns everything when search text is empty', () => {
    expect(filterTrailers(trailers, { searchText: '' })).toEqual(trailers);
  });
});

describe('sortTrailers', () => {
  it('sorts by label ascending', () => {
    expect(sortTrailers(trailers, 'label-asc').map((t) => t.label)).toEqual(['T100 - Amber', 'T231 - Zed']);
  });

  it('sorts by label descending', () => {
    expect(sortTrailers(trailers, 'label-desc').map((t) => t.label)).toEqual(['T231 - Zed', 'T100 - Amber']);
  });
});

describe('paginateTrailers', () => {
  it('slices to the requested page', () => {
    expect(paginateTrailers(trailers, 1, 1)).toEqual([trailers[0]]);
    expect(paginateTrailers(trailers, 2, 1)).toEqual([trailers[1]]);
  });
});
