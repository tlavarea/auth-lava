import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TrailerListingRow } from '../trailers.models';
import { TrailerTable } from './trailer-table';

describe('TrailerTable', () => {
  let fixture: ComponentFixture<TrailerTable>;

  const trailers: TrailerListingRow[] = [
    { id: 'trailer-1', label: 'T231 - Zed', manufacturer: 'Great Dane', year: 2022, currentTruckNumber: 'T1000' },
    { id: 'trailer-2', label: 'T100 - Amber', manufacturer: null, year: null, currentTruckNumber: null },
  ];

  function searchInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[hlmInputGroupInput]');
  }

  function cards(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('a[data-slot="item"]'));
  }

  function typeSearch(value: string): void {
    const input = searchInput();
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TrailerTable],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TrailerTable);
    fixture.componentRef.setInput('trailers', trailers);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a card per trailer, label-sorted ascending by default', () => {
    expect(cards().length).toBe(2);

    const [firstCard, secondCard] = cards();
    expect(firstCard.textContent).toContain('T100 - Amber');
    expect(secondCard.textContent).toContain('T231 - Zed');
  });

  it('shows manufacturer/year/current truck or a fallback', () => {
    const [firstCard, secondCard] = cards();
    expect(firstCard.textContent).toContain('—');
    expect(secondCard.textContent).toContain('Great Dane');
    expect(secondCard.textContent).toContain('2022');
    expect(secondCard.textContent).toContain('T1000');
  });

  it('narrows results by search text and shows a removable filter chip', () => {
    typeSearch('Zed');

    expect(cards().length).toBe(1);
    expect(cards()[0].textContent).toContain('T231 - Zed');

    const removeChip: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      'button[aria-label^="Remove filter"]'
    );
    expect(removeChip).toBeTruthy();

    removeChip?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });

  it('shows an empty state with a clear-filters action when no trailer matches', () => {
    typeSearch('no such trailer');

    expect(cards().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('No trailers match your filters');

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const clearFilters = buttons.find((button) => button.textContent?.trim() === 'Clear filters');
    expect(clearFilters).toBeTruthy();

    clearFilters?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });
});
