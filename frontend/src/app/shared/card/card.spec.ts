import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Card } from './card';

describe('Card', () => {
  let fixture: ComponentFixture<Card>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Card],
    }).compileComponents();

    fixture = TestBed.createComponent(Card);
  });

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('omits the header entirely when neither title nor description is provided', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[hlmCardHeader]')).toBeNull();
  });

  it('renders the title and description when both are provided', () => {
    fixture.componentRef.setInput('title', 'Profile');
    fixture.componentRef.setInput('description', 'Manage your account details');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Profile');
    expect(fixture.nativeElement.querySelector('[hlmCardDescription]').textContent).toContain(
      'Manage your account details'
    );
  });

  it('renders only the title when no description is given', () => {
    fixture.componentRef.setInput('title', 'Profile');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[hlmCardHeader]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Profile');
    expect(fixture.nativeElement.querySelector('[hlmCardDescription]')).toBeNull();
  });

  it('renders only the description when no title is given', () => {
    fixture.componentRef.setInput('description', 'Manage your account details');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('h1')).toBeNull();
    expect(fixture.nativeElement.querySelector('[hlmCardDescription]').textContent).toContain(
      'Manage your account details'
    );
  });

  it('applies contentClass to the content container', () => {
    fixture.componentRef.setInput('contentClass', ['flex', 'flex-col']);
    fixture.detectChanges();

    const content: HTMLElement = fixture.nativeElement.querySelector('[hlmCardContent]');
    expect(content.classList.contains('flex')).toBe(true);
    expect(content.classList.contains('flex-col')).toBe(true);
  });
});

@Component({
  imports: [Card],
  template: `<app-card><span>Projected content</span></app-card>`,
})
class CardHost {}

describe('Card content projection', () => {
  it('projects content into the card', async () => {
    await TestBed.configureTestingModule({
      imports: [CardHost],
    }).compileComponents();

    const fixture: ComponentFixture<CardHost> = TestBed.createComponent(CardHost);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Projected content');
  });
});
