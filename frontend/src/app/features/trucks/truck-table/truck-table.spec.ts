import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TruckListingRow } from '../trucks.models';
import { TruckTable } from './truck-table';

describe('TruckTable', () => {
  let fixture: ComponentFixture<TruckTable>;

  const trucks: TruckListingRow[] = [
    {
      id: 'truck-1',
      truckNumber: 'T2000',
      engineState: 'On',
      ecuSpeedMph: 62.5,
      currentDriverName: 'Zoe Adams',
      currentTrailerLabel: "T231 - 53' SDL",
    },
    {
      id: 'truck-2',
      truckNumber: 'T1000',
      engineState: null,
      ecuSpeedMph: null,
      currentDriverName: null,
      currentTrailerLabel: null,
    },
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
      imports: [TruckTable],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TruckTable);
    fixture.componentRef.setInput('trucks', trucks);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders a card per truck, truck-number-sorted ascending by default', () => {
    expect(cards().length).toBe(2);

    const [firstCard, secondCard] = cards();
    expect(firstCard.textContent).toContain('T1000');
    expect(secondCard.textContent).toContain('T2000');
  });

  it('shows current driver/trailer or a fallback, and the derived status badge', () => {
    const [firstCard, secondCard] = cards();
    expect(firstCard.textContent).toContain('—');
    expect(firstCard.textContent).toContain('Unknown');
    expect(secondCard.textContent).toContain('Zoe Adams');
    expect(secondCard.textContent).toContain("T231 - 53' SDL");
    expect(secondCard.textContent).toContain('Moving');
  });

  it('narrows results by search text and shows a removable filter chip', () => {
    typeSearch('Zoe');

    expect(cards().length).toBe(1);
    expect(cards()[0].textContent).toContain('Zoe Adams');

    const removeChip: HTMLButtonElement | null = fixture.nativeElement.querySelector(
      'button[aria-label^="Remove filter"]'
    );
    expect(removeChip).toBeTruthy();

    removeChip?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });

  it('shows an empty state with a clear-filters action when no truck matches', () => {
    typeSearch('no such truck');

    expect(cards().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('No trucks match your filters');

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const clearFilters = buttons.find((button) => button.textContent?.trim() === 'Clear filters');
    expect(clearFilters).toBeTruthy();

    clearFilters?.click();
    fixture.detectChanges();
    expect(cards().length).toBe(2);
  });
});
