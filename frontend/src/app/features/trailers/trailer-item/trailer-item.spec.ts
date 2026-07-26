import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TrailerListingRow } from '../trailers.models';
import { TrailerItem } from './trailer-item';

describe('TrailerItem', () => {
  let fixture: ComponentFixture<TrailerItem>;

  const trailers: TrailerListingRow[] = [
    { id: 'trailer-1', label: "T231 - 53' SDL", manufacturer: 'Great Dane', year: 2022, currentTruckNumber: 'T1000' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TrailerItem],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TrailerItem);
    fixture.componentRef.setInput('trailers', trailers);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the whole row as a link to its detail route', () => {
    expect(fixture.nativeElement.textContent).toContain("T231 - 53' SDL");
    expect(fixture.nativeElement.textContent).toContain('On T1000');

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/trailer-1"]');
    expect(row).toBeTruthy();
    expect(row?.querySelector('button, a')).toBeFalsy();
  });

  it('shows a fallback when the trailer has no current truck', () => {
    fixture.componentRef.setInput('trailers', [{ ...trailers[0], currentTruckNumber: null }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No truck assigned');
  });

  it('highlights the row matching selectedId and marks it aria-current', () => {
    fixture.componentRef.setInput('selectedId', 'trailer-1');
    fixture.detectChanges();

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/trailer-1"]');
    expect(row?.getAttribute('aria-current')).toBe('page');
    expect(row?.classList.contains('bg-accent')).toBe(true);
  });

  it('does not highlight any row when selectedId is null', () => {
    expect(fixture.nativeElement.querySelector('[aria-current="page"]')).toBeFalsy();
  });
});
