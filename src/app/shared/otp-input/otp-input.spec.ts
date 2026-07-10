import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OtpInput } from './otp-input';

describe('OtpInput', () => {
  let component: OtpInput;
  let fixture: ComponentFixture<OtpInput>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OtpInput],
    }).compileComponents();

    fixture = TestBed.createComponent(OtpInput);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('inputId', 'otp');
    fixture.componentRef.setInput('maxLength', 6);
    await fixture.whenStable();
  });

  function nativeInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('#otp');
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders one slot per maxLength', () => {
    expect(fixture.nativeElement.querySelectorAll('hlm-input-otp-slot').length).toBe(6);
  });

  it('re-renders slots when maxLength changes', async () => {
    fixture.componentRef.setInput('maxLength', 4);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('hlm-input-otp-slot').length).toBe(4);
  });

  it('applies inputId to the underlying native input', () => {
    expect(nativeInput()).toBeTruthy();
  });

  it('reflects the disabled input on the native input', async () => {
    fixture.componentRef.setInput('disabled', true);
    await fixture.whenStable();

    expect(nativeInput().disabled).toBe(true);
  });

  it('emits valueChange as characters are entered', async () => {
    const values: string[] = [];
    component.valueChange.subscribe((value: string): number => values.push(value));

    const input: HTMLInputElement = nativeInput();
    input.value = '123';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(values).toEqual(['123']);
  });

  it('emits completed once maxLength characters are entered', async () => {
    let completedCount = 0;
    component.completed.subscribe((): number => ++completedCount);

    const input: HTMLInputElement = nativeInput();
    input.value = '123456';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(completedCount).toBe(1);
  });

  it('does not emit completed when fewer than maxLength characters are entered', async () => {
    let completedCount = 0;
    component.completed.subscribe((): number => ++completedCount);

    const input: HTMLInputElement = nativeInput();
    input.value = '123';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(completedCount).toBe(0);
  });
});
