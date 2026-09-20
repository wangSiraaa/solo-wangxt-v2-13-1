import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <span class="brand">TM Hub</span>
      <nav>
        <a routerLink="/batches" routerLinkActive="active">Batches</a>
        <a routerLink="/tasks" routerLinkActive="active">Migration tasks</a>
        <a routerLink="/versions" routerLinkActive="active">Versions</a>
      </nav>
    </header>
    <main>
      <router-outlet></router-outlet>
    </main>
  `
})
export class AppComponent {}
