import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HosClockRing } from './hos-clock-ring';

describe('HosClockRing', () => {
  let fixture: ComponentFixture<HosClockRing>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HosClockRing],
    }).compileComponents();

    fixture = TestBed.createComponent(HosClockRing);
    fixture.componentRef.setInput('label', 'Drive');
    fixture.componentRef.setInput('remainingMs', 4 * 3_600_000 + 16 * 60_000);
    fixture.componentRef.setInput('totalMs', 11 * 3_600_000);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the formatted remaining time and label', () => {
    expect(fixture.nativeElement.textContent).toContain('4:16');
    expect(fixture.nativeElement.textContent).toContain('Drive');
  });

  it('exposes the remaining time as an aria-label rather than color alone', () => {
    const ring: HTMLElement = fixture.nativeElement.querySelector('[role="img"]');
    expect(ring.getAttribute('aria-label')).toBe('Drive: 4:16 remaining');
  });

  it('treats a null remainingMs as 0:00 and an empty ring', () => {
    fixture.componentRef.setInput('remainingMs', null);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('0:00');
    const ring: HTMLElement = fixture.nativeElement.querySelector('[role="img"]');
    expect(ring.getAttribute('aria-label')).toBe('Drive: 0:00 remaining');
  });

  it('clamps dash offset so remainingMs never exceeding totalMs overflows the ring', () => {
    fixture.componentRef.setInput('remainingMs', 20 * 3_600_000);
    fixture.detectChanges();

    const [, progressCircle] = fixture.nativeElement.querySelectorAll('circle');
    expect(Number(progressCircle.getAttribute('stroke-dashoffset'))).toBeCloseTo(0, 5);
  });

  it('renders a bigger, thicker, blue progress ring', () => {
    const [trackCircle, progressCircle]: HTMLElement[] = fixture.nativeElement.querySelectorAll('circle');
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');

    expect(svg.getAttribute('class')).toContain('h-20 w-20');
    expect(trackCircle.getAttribute('stroke-width')).toBe('7');
    expect(progressCircle.getAttribute('stroke-width')).toBe('7');
    expect(progressCircle.getAttribute('class')).toContain('stroke-blue-600');
  });
});
