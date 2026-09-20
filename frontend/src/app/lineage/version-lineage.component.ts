import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../api.service';
import { TmVersion } from '../models';

@Component({
  selector: 'app-version-lineage',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>Memory-base versions</h2>
    <table class="grid">
      <thead>
        <tr>
          <th>ID</th><th>Label</th><th>Status</th><th>Effective</th><th>Parent</th>
          <th>Checksum</th><th>Batches</th><th>Conflict records</th><th>Rollback reason</th>
          <th>Download</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr *ngFor="let v of versions" [class.effective]="v.effective">
          <td>{{ v.id }}</td>
          <td>{{ v.label }}</td>
          <td>{{ v.status }}</td>
          <td>{{ v.effective ? '●' : '' }}</td>
          <td>{{ v.parentId ?? '—' }}</td>
          <td class="mono">{{ v.checksum | slice:0:12 }}…</td>
          <td class="mono small">{{ v.batchManifest }}</td>
          <td class="mono small">{{ v.conflictReport }}</td>
          <td class="small">{{ v.rollbackReason }}</td>
          <td><a [href]="v.downloadUrl" *ngIf="v.downloadUrl">TMX</a></td>
          <td>
            <button (click)="showLineage(v.id)">Lineage</button>
            <input [(ngModel)]="reasonInputs[v.id]" placeholder="rollback reason" />
            <button (click)="rollback(v.id)" [disabled]="!reasonInputs[v.id]">Rollback to this</button>
          </td>
        </tr>
      </tbody>
    </table>

    <aside class="panel" *ngIf="lineage.length">
      <h3>Lineage of version {{ lineage[0].id }}</h3>
      <ol class="chain">
        <li *ngFor="let v of lineage">
          <strong>v{{ v.id }}</strong> — {{ v.label }}
          <span class="mono small">(parent {{ v.parentId ?? '—' }}, {{ v.status }},
            checksum {{ v.checksum | slice:0:12 }}…)</span>
        </li>
      </ol>
    </aside>
  `,
  styles: [`
    .grid { width: 100%; border-collapse: collapse; background: #fff; }
    .grid th, .grid td { border: 1px solid #dde4ea; padding: 8px; font-size: 13px;
      text-align: left; vertical-align: top; }
    .grid th { background: #eef2f6; }
    tr.effective { background: #eefaf0; }
    .mono { font-family: ui-monospace, monospace; }
    .small { font-size: 11px; max-width: 220px; word-break: break-all; }
    button { padding: 4px 10px; border: 0; border-radius: 4px; background: #16324f;
      color: #fff; cursor: pointer; margin: 0 4px 4px 0; }
    button:disabled { opacity: .4; }
    input { padding: 4px 8px; border: 1px solid #b9c4cf; border-radius: 4px; font-size: 12px; }
    .panel { margin-top: 18px; padding: 14px; background: #fff; border: 1px solid #dde4ea;
      border-radius: 6px; }
    .chain li { margin-bottom: 6px; }
  `]
})
export class VersionLineageComponent implements OnInit {
  private api = inject(ApiService);

  versions: TmVersion[] = [];
  lineage: TmVersion[] = [];
  reasonInputs: Record<number, string> = {};

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.api.versions().subscribe(vs => this.versions = vs);
  }

  showLineage(id: number): void {
    this.api.lineage(id).subscribe(ls => this.lineage = ls);
  }

  rollback(id: number): void {
    this.api.rollback(id, this.reasonInputs[id]).subscribe(() => {
      this.lineage = [];
      this.reload();
    });
  }
}
