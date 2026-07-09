import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OauthProviders } from './oauth-providers';

describe('OauthProviders', () => {
  let component: OauthProviders;
  let fixture: ComponentFixture<OauthProviders>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OauthProviders],
    }).compileComponents();

    fixture = TestBed.createComponent(OauthProviders);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render a button for each OAuth provider', () => {
    const buttons: HTMLButtonElement[] = fixture.nativeElement.querySelectorAll('button');
    const labels = Array.from(buttons).map((button) => button.textContent?.trim());
    expect(labels).toContain('Continue with Google');
    expect(labels).toContain('Continue with GitHub');
  });
});
