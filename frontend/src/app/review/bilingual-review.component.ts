import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../api.service';
import { Batch, Candidate } from '../models';

@Component({
  selector: 'app-bilingual-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="toolbar">
      <label>Batch:</label>
      <select [(ngModel)]="selectedBatchId" (ngModelChange)="loadCandidates()">
        <option *ngFor="let b of batches" [ngValue]="b.id">
          #{{ b.id }} · {{ b.vendor }} · {{ b.sourceLang }}→{{ b.targetLang }} · {{ b.productLine }}
          · {{ b.status }} · fp {{ b.tmxFingerprint | slice:0:8 }}
        </option>
      </select>
      <label>Reviewer:</label>
      <input [(ngModel)]="reviewer" placeholder="name" />
      <button (click)="runMigration()" [disabled]="!selectedBatchId">Run migration</button>
      <button (click)="publish()" [disabled]="!selectedBatchId">Publish</button>
      <span class="msg" [class.err]="isError" *ngIf="message">{{ message }}</span>
    </section>

    <table class="grid" *ngIf="candidates.length">
      <thead>
        <tr>
          <th>#</th><th>Source ({{ sourceLang }})</th><th>Target ({{ targetLang }})</th>
          <th>Placeholder diff</th><th>Origin</th><th>Status</th><th>Review</th>
        </tr>
      </thead>
      <tbody>
        <ng-container *ngFor="let c of candidates">
          <tr [class]="'row-' + c.status.toLowerCase()">
            <td>{{ c.id }}</td>
            <td class="seg" [innerHTML]="highlight(c.sourceText)"></td>
            <td class="seg" [innerHTML]="highlight(c.targetText)"></td>
            <td class="diff">
              <span *ngIf="c.placeholderDiff; else okPh">{{ c.placeholderDiff }}</span>
              <ng-template #okPh><span class="ok">✓ preserved</span></ng-template>
            </td>
            <td class="origin">
              batch #{{ c.batchId }}<br />
              <small>{{ c.productLine }}</small>
            </td>
            <td>
              <span class="badge" [class]="'b-' + c.status.toLowerCase()">{{ c.status }}</span>
              <div class="anomaly" *ngIf="c.anomalyType">⚠ {{ c.anomalyType }}<br />
                <small>{{ c.anomalyDetail }}</small></div>
              <div *ngIf="c.decidedBy" class="decided">
                by {{ c.decidedBy }} · v{{ c.version }}
                <span *ngIf="c.termOverride">· term “{{ c.termOverride }}”</span>
              </div>
            </td>
            <td class="actions" *ngIf="isActionable(c); else done">
              <button (click)="decide(c, 'ACCEPT')">Accept</button>
              <button (click)="decide(c, 'REJECT')">Reject</button>
              <div class="resolve" *ngIf="c.status === 'BLOCKED'">
                <input [(ngModel)]="termInputs[c.id]" placeholder="final term (polysemy)" />
                <input [(ngModel)]="correctionInputs[c.id]" placeholder="corrected target" />
                <button (click)="resolve(c)">Resolve</button>
              </div>
              <div class="resolve" *ngIf="c.status === 'CONFLICT'">
                <input [(ngModel)]="termInputs[c.id]" placeholder="final term (optional)" />
                <button (click)="showConflict(c)">Conflict chain ({{ c.conflictGroupId }})</button>
              </div>
            </td>
            <ng-template #done><td class="actions muted">decided</td></ng-template>
          </tr>
        </ng-container>
      </tbody>
    </table>

    <aside class="panel" *ngIf="conflictChain.length">
      <h3>Conflict chain — group {{ activeGroup }}</h3>
      <p class="hint">Same source sentence, diverging targets across vendors. Decide each candidate
        individually; the first accepted decision resolves the group.</p>
      <table class="grid">
        <thead><tr><th>Candidate</th><th>Batch / vendor</th><th>Target</th><th>Status</th></tr></thead>
        <tbody>
          <tr *ngFor="let c of conflictChain">
            <td>#{{ c.id }}</td>
            <td>batch #{{ c.batchId }}</td>
            <td class="seg" [innerHTML]="highlight(c.targetText)"></td>
            <td><span class="badge" [class]="'b-' + c.status.toLowerCase()">{{ c.status }}</span></td>
          </tr>
        </tbody>
      </table>
      <button (click)="conflictChain = []">Close</button>
    </aside>
  `,
  styles: [`
    .toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; flex-wrap: wrap; }
    select, input { padding: 5px 8px; border: 1px solid #b9c4cf; border-radius: 4px; }
    button { padding: 5px 12px; border: 0; border-radius: 4px; background: #16324f; color: #fff;
      cursor: pointer; } button:disabled { opacity: .4; cursor: default; }
    .msg { font-size: 13px; color: #1c7a34; } .msg.err { color: #b3261e; }
    .grid { width: 100%; border-collapse: collapse; background: #fff; }
    .grid th, .grid td { border: 1px solid #dde4ea; padding: 8px; vertical-align: top;
      font-size: 13px; text-align: left; }
    .grid th { background: #eef2f6; }
    .seg { min-width: 180px; white-space: pre-wrap; }
    .diff { color: #b3261e; font-size: 12px; } .diff .ok { color: #1c7a34; }
    .origin small { color: #6b7a89; }
    .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px;
      font-weight: 600; color: #fff; }
    .b-pending { background: #8a97a5; } .b-conflict { background: #c77700; }
    .b-blocked { background: #b3261e; } .b-accepted { background: #1c7a34; }
    .b-rejected { background: #5c6670; } .b-migrated { background: #0b5cad; }
    .anomaly { margin-top: 4px; font-size: 11px; color: #b3261e; }
    .decided { margin-top: 4px; font-size: 11px; color: #6b7a89; }
    .actions button { margin: 0 4px 4px 0; font-size: 12px; }
    .actions.muted { color: #98a4b0; font-size: 12px; }
    .resolve input { display: block; margin-bottom: 4px; width: 180px; font-size: 12px; }
    .row-blocked { background: #fdf3f2; } .row-conflict { background: #fff8ec; }
    .panel { margin-top: 18px; padding: 14px; background: #fff; border: 1px solid #dde4ea;
      border-radius: 6px; }
    .hint { font-size: 12px; color: #6b7a89; }
    :host ::ng-deep mark.ph { background: #ffe9a8; padding: 0 2px; border-radius: 2px; }
    :host ::ng-deep mark.term { background: #cde9ff; padding: 0 2px; border-radius: 2px; }
  `]
})
export class BilingualReviewComponent implements OnInit {
  private api = inject(ApiService);

  batches: Batch[] = [];
  candidates: Candidate[] = [];
  conflictChain: Candidate[] = [];
  activeGroup: number | null = null;
  selectedBatchId!: number;
  reviewer = 'reviewer-1';
  message = '';
  isError = false;
  termInputs: Record<number, string> = {};
  correctionInputs: Record<number, string> = {};

  ngOnInit(): void {
    this.api.listBatches().subscribe(bs => {
      this.batches = bs;
      if (bs.length) { this.selectedBatchId = bs[0].id; this.loadCandidates(); }
    });
  }

  get sourceLang(): string { return this.candidates[0]?.sourceLang ?? ''; }
  get targetLang(): string { return this.candidates[0]?.targetLang ?? ''; }

  loadCandidates(): void {
    if (!this.selectedBatchId) return;
    this.api.candidates(this.selectedBatchId).subscribe(cs => this.candidates = cs);
  }

  isActionable(c: Candidate): boolean {
    return c.status === 'PENDING' || c.status === 'CONFLICT' || c.status === 'BLOCKED';
  }

  /** Highlights placeholder tokens (replacement positions) in a segment. */
  highlight(text: string): string {
    const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return esc.replace(/(\\?\{\d+\}|\\?\{\{[A-Za-z0-9_.]+\}\}|\\?%\d+\$?[sd]|\\?%[sd])/g,
      '<mark class="ph">$1</mark>');
  }

  decide(c: Candidate, action: 'ACCEPT' | 'REJECT'): void {
    this.api.decide(c.id, {
      reviewer: this.reviewer, action,
      termOverride: this.termInputs[c.id] || null,
      reason: null, expectedVersion: c.version
    }).subscribe({
      next: () => { this.flash(`candidate ${c.id} ${action.toLowerCase()}ed`); this.loadCandidates(); },
      error: err => this.handleError(err, c)
    });
  }

  resolve(c: Candidate): void {
    this.api.decide(c.id, {
      reviewer: this.reviewer, action: 'RESOLVE',
      termOverride: this.termInputs[c.id] || null,
      correctedTarget: this.correctionInputs[c.id] || null,
      reason: 'manual resolution', expectedVersion: c.version
    }).subscribe({
      next: () => { this.flash(`candidate ${c.id} resolved`); this.loadCandidates(); },
      error: err => this.handleError(err, c)
    });
  }

  showConflict(c: Candidate): void {
    if (!c.conflictGroupId) return;
    this.activeGroup = c.conflictGroupId;
    this.api.conflictChain(c.conflictGroupId).subscribe(cs => this.conflictChain = cs);
  }

  runMigration(): void {
    this.api.createTask(this.selectedBatchId).subscribe(t =>
      this.api.runTask(t.id).subscribe(done =>
        this.flash(`task ${done.id}: ${done.status}, committed ${done.committedCount}`)));
  }

  publish(): void {
    this.api.publish(`release-${Date.now()}`, [this.selectedBatchId]).subscribe({
      next: v => this.flash(`published version ${v.id} (${v.checksum.slice(0, 8)}…)`),
      error: err => {
        const blockers = err.error?.blockers as string[] | undefined;
        this.flash(`publish blocked: ${(blockers ?? [err.error?.message]).join(' | ')}`, true);
      }
    });
  }

  private handleError(err: any, c: Candidate): void {
    if (err.status === 409) {
      // version conflict: another reviewer was faster — refresh, never double-decide
      this.flash(`version conflict on candidate ${c.id}: ${err.error?.message ?? err.message}`, true);
      this.loadCandidates();
    } else if (err.status === 422) {
      this.flash(`cannot accept: ${err.error?.message ?? err.message}`, true);
    } else {
      this.flash(err.error?.message ?? err.message ?? 'error', true);
    }
  }

  private flash(msg: string, isError = false): void {
    this.message = msg;
    this.isError = isError;
    setTimeout(() => { if (this.message === msg) this.message = ''; }, 12000);
  }
}
