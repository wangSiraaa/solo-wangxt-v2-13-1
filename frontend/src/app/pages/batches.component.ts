import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../api.service';
import { Batch } from '../models';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <h2>Incremental batches</h2>
    <table>
      <thead>
        <tr>
          <th>ID</th><th>Vendor</th><th>Language pair</th><th>Product line</th>
          <th>Source version</th><th>TMX fingerprint</th><th>Shards</th><th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr *ngFor="let b of batches">
          <td><a [routerLink]="['/batches', b.id]">#{{ b.id }}</a></td>
          <td>{{ b.vendor }}</td>
          <td>{{ b.sourceLang }} → {{ b.targetLang }}</td>
          <td>{{ b.productLine }}</td>
          <td>v{{ b.sourceVersionId }}</td>
          <td class="mono">{{ b.tmxFingerprint | slice:0:12 }}…</td>
          <td>{{ b.receivedShards }}/{{ b.expectedShards }}</td>
          <td><span class="badge" [ngClass]="b.status">{{ b.status }}</span></td>
        </tr>
      </tbody>
    </table>
    <p *ngIf="batches.length === 0" class="muted">No batches yet.</p>
  `
})
export class BatchesComponent implements OnInit {
  batches: Batch[] = [];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.listBatches().subscribe(b => (this.batches = b));
  }
}
