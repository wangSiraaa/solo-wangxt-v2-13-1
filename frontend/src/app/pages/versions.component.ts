import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../api.service';
import { Lineage, TmVersion } from '../models';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>Memory versions</h2>
    <div *ngIf="error" class="error-banner">{{ error }}</div>
    <table>
      <thead>
        <tr>
          <th>ID</th><th>Label</th><th>Kind</th><th>Parent</th><th>Checksum</th>
          <th>Reason</th><th>Created</th><th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr *ngFor="let v of versions">
          <td>
            v{{ v.id }}
            <span *ngIf="lineage && lineage.effectiveVersionId === v.id" class="badge READY">effective</span>
          </td>
          <td>{{ v.label }}</td>
          <td><span class="badge" [ngClass]="v.kind">{{ v.kind }}</span></td>
          <td>{{ v.parentId ? 'v' + v.parentId : '—' }}</td>
          <td class="mono">{{ v.checksum ? (v.checksum | slice:0:12) + '…' : '—' }}</td>
          <td class="muted">{{ v.reason }}</td>
          <td class="mono">{{ v.createdAt | date:'yyyy-MM-dd HH:mm' }} by {{ v.createdBy }}</td>
          <td>
            <button (click)="showLineage(v)">lineage</button>
            <button (click)="export(v)">export TMX</button>
            <a [href]="downloadUrl(v)" target="_blank"><button>download</button></a>
            <button class="danger" (click)="rollback(v)">rollback</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="panel" *ngIf="lineage">
      <h3>Version lineage</h3>
      <div *ngFor="let node of lineage.chain" class="panel">
        <b>v{{ node.version.id }} — {{ node.version.label }}</b>
        <span class="badge" [ngClass]="node.version.kind">{{ node.version.kind }}</span>
        <span *ngIf="node.effective" class="badge READY">effective</span>
        <div class="muted">
          parent: {{ node.version.parentId ? 'v' + node.version.parentId : '—' }} ·
          checksum: <span class="mono">{{ node.version.checksum }}</span>
          <span *ngIf="node.version.reason"> · reason: {{ node.version.reason }}</span>
        </div>
        <div *ngIf="node.batchIds.length">
          batches:
          <span *ngFor="let b of node.batchIds" class="ph">#{{ b }}</span>
        </div>
        <div *ngIf="node.conflictResolutions.length">
          conflict resolutions:
          <div *ngFor="let r of node.conflictResolutions" class="muted">
            group #{{ r.conflictGroupId }} → candidate #{{ r.resolvedCandidateId }}
            by {{ r.resolvedBy }} <span *ngIf="r.note">— {{ r.note }}</span>
          </div>
        </div>
      </div>
    </div>
  `
})
export class VersionsComponent implements OnInit {
  versions: TmVersion[] = [];
  lineage: Lineage | null = null;
  error: string | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.api.listVersions().subscribe(v => {
      this.versions = v.sort((a, b) => a.id - b.id);
      if (this.versions.length > 0 && !this.lineage) {
        this.showLineage(this.versions[this.versions.length - 1]);
      }
    });
  }

  showLineage(v: TmVersion): void {
    this.api.lineage(v.id).subscribe(l => (this.lineage = l));
  }

  export(v: TmVersion): void {
    this.api.exportVersion(v.id).subscribe(() => (this.error = null));
  }

  downloadUrl(v: TmVersion): string {
    return this.api.downloadUrl(v.id);
  }

  rollback(v: TmVersion): void {
    const reason = window.prompt('Rollback reason (required):');
    if (!reason) {
      return;
    }
    this.api.rollback(v.id, 'REVERSE_VERSION', reason, 'ui-user').subscribe({
      next: () => {
        this.error = null;
        this.lineage = null;
        this.refresh();
      },
      error: err => (this.error = err.error?.message ?? 'rollback failed')
    });
  }
}
