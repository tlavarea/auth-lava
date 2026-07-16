import { DIALOG_DATA } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';

import { ShipmentsStore, ShipmentsStoreType } from '../shipments.store';
import { DeclineOfferDialog, DeclineOfferDialogContext } from './decline-offer.dialog';

function clickButtonWithText(fixture: ComponentFixture<unknown>, text: string): void {
  const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
  const button: HTMLButtonElement = buttons.find(
    (candidate: HTMLButtonElement) => candidate.textContent?.trim() === text
  ) as HTMLButtonElement;
  button.click();
}

async function flushAsync(fixture: ComponentFixture<unknown>): Promise<void> {
  await fixture.whenStable();
  await new Promise((resolve) => setTimeout(resolve, 0));
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('DeclineOfferDialog', () => {
  let component: DeclineOfferDialog;
  let fixture: ComponentFixture<DeclineOfferDialog>;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [DeclineOfferDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ShipmentsStore,
        { provide: BrnDialogRef, useValue: dialogRef },
        {
          provide: DIALOG_DATA,
          useFactory: (store: ShipmentsStoreType): DeclineOfferDialogContext => ({ offerId: 42, store }),
          deps: [ShipmentsStore],
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(DeclineOfferDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('shows the confirmation copy', () => {
    expect(fixture.nativeElement.textContent).toContain('Decline offer');
    expect(fixture.nativeElement.textContent).toContain('Are you sure you want to decline this offer?');
  });

  it('"Cancel" closes the dialog without calling the API', () => {
    clickButtonWithText(fixture, 'Cancel');

    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('"Decline" submits and closes the dialog on success', async () => {
    clickButtonWithText(fixture, 'Decline');

    const req = httpMock.expectOne('/api/sw-expedited/shipments/42/respond');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ response: 'DECLINE', conveyancesAvailable: 0 });
    req.flush(null);
    await flushAsync(fixture);

    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('shows the stubbed-backend message and does not close the dialog on a 501', async () => {
    clickButtonWithText(fixture, 'Decline');

    httpMock
      .expectOne('/api/sw-expedited/shipments/42/respond')
      .flush(null, { status: 501, statusText: 'Not Implemented' });
    await flushAsync(fixture);

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain("This feature isn't available yet.");
  });
});
