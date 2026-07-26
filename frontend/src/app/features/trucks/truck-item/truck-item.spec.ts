import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TruckListingRow } from '../trucks.models';
import { TruckItem } from './truck-item';

describe('TruckItem', () => {
  let fixture: ComponentFixture<TruckItem>;

  const trucks: TruckListingRow[] = [
    {
      id: 'truck-1',
      truckNumber: 'T1000',
      statusCode: 1,
      currentDriverName: 'Jane Trucker',
      currentTrailerLabel: "T231 - 53' SDL",
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TruckItem],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TruckItem);
    fixture.componentRef.setInput('trucks', trucks);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the whole row as a link to its detail route', () => {
    expect(fixture.nativeElement.textContent).toContain('T1000');
    expect(fixture.nativeElement.textContent).toContain('Jane Trucker');

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/truck-1"]');
    expect(row).toBeTruthy();
    expect(row?.querySelector('button, a')).toBeFalsy();
  });

  it('shows a fallback when the truck has no current driver', () => {
    fixture.componentRef.setInput('trucks', [{ ...trucks[0], currentDriverName: null }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No driver assigned');
  });

  it('highlights the row matching selectedId and marks it aria-current', () => {
    fixture.componentRef.setInput('selectedId', 'truck-1');
    fixture.detectChanges();

    const row: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/truck-1"]');
    expect(row?.getAttribute('aria-current')).toBe('page');
    expect(row?.classList.contains('bg-accent')).toBe(true);
  });

  it('does not highlight any row when selectedId is null', () => {
    expect(fixture.nativeElement.querySelector('[aria-current="page"]')).toBeFalsy();
  });
});
