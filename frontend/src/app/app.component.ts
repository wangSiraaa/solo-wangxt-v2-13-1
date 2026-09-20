import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <span class="brand">TM Platform</span>
      <nav>
        <a routerLink="/review" routerLinkActive="active">Bilingual review</a>
        <a routerLink="/versions" routerLinkActive="active">Versions &amp; lineage</a>
      </nav>
    </header>
    <main><router-outlet /></main>
  `,
  styles: [`
    .topbar { display: flex; align-items: center; gap: 24px; padding: 10px 20px;
      background: #16324f; color: #fff; }
    .brand { font-weight: 700; letter-spacing: .5px; }
    nav a { color: #b9cbdd; margin-right: 16px; text-decoration: none; font-size: 14px; }
    nav a.active { color: #fff; border-bottom: 2px solid #6db3f2; padding-bottom: 2px; }
    main { padding: 16px 20px; }
  `]
})
export class AppComponent {}
