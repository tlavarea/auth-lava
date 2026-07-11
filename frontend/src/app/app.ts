import { Component, ElementRef, inject, Signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Event, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly router: Router = inject(Router);
  private readonly mainContent: Signal<ElementRef<HTMLElement>> =
    viewChild.required<ElementRef<HTMLElement>>('mainContent');

  constructor() {
    this.router.events
      .pipe(
        filter((event: Event): boolean => event instanceof NavigationEnd),
        takeUntilDestroyed()
      )
      .subscribe((): void => this.mainContent().nativeElement.focus());
  }
}
