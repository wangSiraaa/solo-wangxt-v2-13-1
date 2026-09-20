import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../api.service';
import { MigrationTask } from '../models';

@Component({
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>Migration tasks</h2>
    <table>
      <thead>
        <tr>
          <th>ID</th><th>Name</th><th>Batch</th><th>Status</th>
          <th>Progress</th><th>Checkpoint (last candidate)</th><th>Error</th><th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr *ngFor="let t of tasks">
          <td>#{{ t.id }}</td>
          <td>{{ t.name }}</td>
          <td>#{{ t.batchId }}</td>
          <td><span class="badge" [ngClass]="t.status">{{ t.status }}</span></td>
          <td>{{ t.committedCount }}/{{ t.total }} committed</td>
          <td class="mono">{{ t.lastCandidateId }}</td>
          <td class="muted">{{ t.error }}</td>
          <td>
            <button class="primary" *ngIf="t.status === 'PENDING'" (click)="start(t)">Start</button>
            <button *ngIf="t.status === 'RUNNING'" (click)="pause(t)">Pause</button>
            <button class="primary" *ngIf="t.status === 'PAUSED' || t.status === 'FAILED' || t.status === 'INTERRUPTED'"
                    (click)="resume(t)">Resume</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p *ngIf="tasks.length === 0" class="muted">No tasks yet.</p>
  `
})
export class TasksComponent implements OnInit {
  tasks: MigrationTask[] = [];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.api.listTasks().subscribe(t => (this.tasks = t));
  }

  start(t: MigrationTask): void {
    this.api.startTask(t.id).subscribe(() => this.refresh());
  }

  pause(t: MigrationTask): void {
    this.api.pauseTask(t.id).subscribe(() => this.refresh());
  }

  resume(t: MigrationTask): void {
    this.api.resumeTask(t.id).subscribe(() => this.refresh());
  }
}
