import { DIALOG_DATA } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';

import { ShipmentsStore, ShipmentsStoreType } from '../shipments.store';
import { AcceptOfferDialog, AcceptOfferDialogContext } from './accept-offer.dialog';

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

describe('AcceptOfferDialog', () => {
  let component: AcceptOfferDialog;
  let fixture: ComponentFixture<AcceptOfferDialog>;
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dialogRef = { close: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [AcceptOfferDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ShipmentsStore,
        { provide: BrnDialogRef, useValue: dialogRef },
        {
          provide: DIALOG_DATA,
          useFactory: (store: ShipmentsStoreType): AcceptOfferDialogContext => ({
            offerId: 42,
            conveyancesAvailable: 3,
            store,
          }),
          deps: [ShipmentsStore],
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(AcceptOfferDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('prefills the conveyances input from the dialog context', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#accept-conveyances-available');

    expect(input.value).toBe('3');
  });

  it('"Cancel" closes the dialog without calling the API', () => {
    clickButtonWithText(fixture, 'Cancel');

    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('"Accept" submits the prefilled conveyances and closes the dialog on success', async () => {
    clickButtonWithText(fixture, 'Accept');

    const req = httpMock.expectOne('/api/sw-expedited/shipments/42/respond');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ response: 'ACCEPT', conveyancesAvailable: 3 });
    req.flush(null);
    await flushAsync(fixture);

    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('shows the stubbed-backend message and does not close the dialog on a 501', async () => {
    clickButtonWithText(fixture, 'Accept');

    httpMock
      .expectOne('/api/sw-expedited/shipments/42/respond')
      .flush(null, { status: 501, statusText: 'Not Implemented' });
    await flushAsync(fixture);

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain("This feature isn't available yet.");
  });
});
