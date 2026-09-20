import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../api.service';
import { Batch, Candidate, CandidateDetail } from '../models';
import { diffTokens, DiffSegment } from '../diff.util';

interface CandidateRow {
  candidate: Candidate;
  detail: CandidateDetail | null;
  expanded: boolean;
  beforeDiff: DiffSegment[];
  afterDiff: DiffSegment[];
  finalDraft: string;
  rationale: string;
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <ng-container *ngIf="batch">
      <h2>
        Batch #{{ batch.id }} — {{ batch.vendor }}
        <span class="badge" [ngClass]="batch.status">{{ batch.status }}</span>
      </h2>
      <p class="muted">
        {{ batch.sourceLang }} → {{ batch.targetLang }} · product line {{ batch.productLine }} ·
        source version v{{ batch.sourceVersionId }} · fingerprint
        <span class="mono">{{ batch.tmxFingerprint | slice:0:16 }}…</span>
      </p>
    </ng-container>

    <div *ngIf="versionConflict" class="conflict-banner">
      ⚠ This candidate was just reviewed by someone else. The page reloaded the latest state —
      your decision was <b>not</b> applied twice.
    </div>
    <div *ngIf="error" class="error-banner">{{ error }}</div>

    <div class="panel">
      <label>Reviewer:</label>
      <input [(ngModel)]="reviewer" placeholder="who is reviewing" />
    </div>

    <table>
      <thead>
        <tr>
          <th style="width:32%">Source</th>
          <th style="width:32%">Proposed target (replacement highlighted)</th>
          <th>Placeholders</th>
          <th>Source of candidate</th>
          <th>Status</th>
          <th>Review</th>
        </tr>
      </thead>
      <tbody>
        <ng-container *ngFor="let row of rows">
          <tr>
            <td class="src-text">
              <ng-container *ngFor="let seg of row.beforeDiff">
                <span [class.diff-removed]="seg.kind === 'removed'">{{ seg.text }}</span>
              </ng-container>
            </td>
            <td class="tgt-text">
              <ng-container *ngFor="let seg of row.afterDiff">
                <span [class.diff-added]="seg.kind === 'added'">{{ seg.text }}</span>
              </ng-container>
              <div *ngIf="row.candidate.finalTarget" class="muted">
                final: <b>{{ row.candidate.finalTarget }}</b>
              </div>
            </td>
            <td>
              <ng-container *ngIf="row.detail as d">
                <span *ngFor="let ph of d.placeholderDiff.sourceTokens" class="ph"
                      [class.ph-missing]="!includes(d.placeholderDiff.targetTokens, ph)">{{ ph }}</span>
                <span *ngIf="d.placeholderDiff.sourceTokens.length === 0" class="muted">—</span>
                <div *ngIf="!placeholderMatch(d)" class="muted">mismatch!</div>
              </ng-container>
            </td>
            <td>
              {{ row.candidate.vendor }}<br/>
              <span class="muted">batch #{{ row.candidate.batchId }}</span>
            </td>
            <td>
              <span class="badge" [ngClass]="row.candidate.status">{{ row.candidate.status }}</span>
              <span *ngIf="row.candidate.committed" class="badge READY">committed</span>
              <div *ngIf="row.detail?.anomalies?.length" class="muted">
                <div *ngFor="let a of row.detail!.anomalies">
                  {{ a.type }} <span *ngIf="a.resolved">✓ ({{ a.resolvedBy }})</span>
                </div>
              </div>
            </td>
            <td>
              <ng-container *ngIf="!isDecided(row.candidate)">
                <button class="primary" (click)="decide(row, 'ACCEPT')">Accept</button>
                <button (click)="decide(row, 'REJECT')">Reject</button>
                <div style="margin-top:4px">
                  <input [(ngModel)]="row.finalDraft" placeholder="final wording (optional)" style="width:90%"/>
                  <input [(ngModel)]="row.rationale" placeholder="rationale" style="width:90%"/>
                  <button (click)="decide(row, 'SET_FINAL')" [disabled]="!row.finalDraft">Set final</button>
                </div>
              </ng-container>
              <span *ngIf="isDecided(row.candidate)" class="muted">decided</span>
              <button (click)="toggle(row)">{{ row.expanded ? 'hide' : 'history & conflicts' }}</button>
            </td>
          </tr>
          <tr *ngIf="row.expanded">
            <td colspan="6">
              <div class="panel">
                <h3>Decision history</h3>
                <table *ngIf="row.detail?.decisions?.length">
                  <thead>
                    <tr><th>When</th><th>Who</th><th>Action</th><th>From → To</th><th>Rationale</th></tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let d of row.detail!.decisions">
                      <td class="mono">{{ d.decidedAt | date:'yyyy-MM-dd HH:mm:ss' }}</td>
                      <td>{{ d.reviewer }}</td>
                      <td>{{ d.action }}</td>
                      <td>{{ d.previousStatus }} → {{ d.newStatus }}</td>
                      <td>{{ d.rationale }}</td>
                    </tr>
                  </tbody>
                </table>
                <p *ngIf="!row.detail?.decisions?.length" class="muted">No decisions yet.</p>

                <h3 *ngIf="row.detail?.conflictChain?.length">Conflict chain</h3>
                <table *ngIf="row.detail?.conflictChain?.length">
                  <thead>
                    <tr><th>Candidate</th><th>Vendor</th><th>Proposed target</th><th>Status</th></tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let c of row.detail!.conflictChain">
                      <td>#{{ c.id }} <span *ngIf="c.id === row.candidate.id">(this)</span></td>
                      <td>{{ c.vendor }}</td>
                      <td class="tgt-text">{{ c.proposedTarget }}</td>
                      <td><span class="badge" [ngClass]="c.status">{{ c.status }}</span></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </td>
          </tr>
        </ng-container>
      </tbody>
    </table>
  `
})
export class BatchDetailComponent implements OnInit {
  batch: Batch | null = null;
  rows: CandidateRow[] = [];
  reviewer = localStorage.getItem('tmhub.reviewer') ?? '';
  versionConflict = false;
  error: string | null = null;

  constructor(private route: ActivatedRoute, private api: ApiService) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getBatch(id).subscribe(b => (this.batch = b));
    this.reload(id);
  }

  private reload(batchId: number): void {
    this.api.candidatesOf(batchId).subscribe(candidates => {
      this.rows = candidates.map(c => {
        const diff = diffTokens(c.sourceText, c.finalTarget ?? c.proposedTarget);
        return {
          candidate: c,
          detail: null,
          expanded: false,
          beforeDiff: diff.before,
          afterDiff: diff.after,
          finalDraft: '',
          rationale: ''
        };
      });
      for (const row of this.rows) {
        this.api.candidateDetail(row.candidate.id).subscribe(d => (row.detail = d));
      }
    });
  }

  toggle(row: CandidateRow): void {
    row.expanded = !row.expanded;
    if (row.expanded) {
      this.api.candidateDetail(row.candidate.id).subscribe(d => (row.detail = d));
    }
  }

  decide(row: CandidateRow, action: 'ACCEPT' | 'REJECT' | 'SET_FINAL'): void {
    this.versionConflict = false;
    this.error = null;
    localStorage.setItem('tmhub.reviewer', this.reviewer);
    this.api.decide(row.candidate.id, this.reviewer || 'anonymous', action,
        action === 'SET_FINAL' ? row.finalDraft : null,
        row.rationale || null, row.candidate.version).subscribe({
      next: updated => {
        row.candidate = updated;
        this.api.candidateDetail(updated.id).subscribe(d => (row.detail = d));
      },
      error: err => {
        if (err.status === 409) {
          // concurrent review: show the version-conflict prompt and reload truth
          this.versionConflict = true;
          this.reload(this.batch!.id);
        } else {
          this.error = err.error?.message ?? 'decision failed';
        }
      }
    });
  }

  isDecided(c: Candidate): boolean {
    return c.status === 'ACCEPTED' || c.status === 'REJECTED';
  }

  includes(list: string[], item: string): boolean {
    return list.includes(item);
  }

  placeholderMatch(d: CandidateDetail): boolean {
    const a = d.placeholderDiff.sourceTokens;
    const b = d.placeholderDiff.targetTokens;
    return a.length === b.length && a.every((x, i) => x === b[i]);
  }
}
