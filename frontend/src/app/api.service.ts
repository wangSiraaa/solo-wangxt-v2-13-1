import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Batch, Candidate, Decision, TaskInfo, TmVersion } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  listBatches(): Observable<Batch[]> {
    return this.http.get<Batch[]>('/api/batches');
  }

  candidates(batchId: number): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(`/api/batches/${batchId}/candidates`);
  }

  conflictChain(groupId: number): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(`/api/batches/conflicts/${groupId}`);
  }

  decide(candidateId: number, body: {
    reviewer: string; action: 'ACCEPT' | 'REJECT' | 'RESOLVE';
    termOverride?: string | null; correctedTarget?: string | null;
    reason?: string | null; expectedVersion: number;
  }): Observable<Candidate> {
    return this.http.post<Candidate>(`/api/candidates/${candidateId}/decisions`, body);
  }

  history(candidateId: number): Observable<Decision[]> {
    return this.http.get<Decision[]>(`/api/candidates/${candidateId}/history`);
  }

  createTask(batchId: number): Observable<TaskInfo> {
    return this.http.post<TaskInfo>(`/api/batches/${batchId}/tasks`, null);
  }

  runTask(taskId: number, chunkSize = 50, maxChunks = 1000): Observable<TaskInfo> {
    return this.http.post<TaskInfo>(`/api/tasks/${taskId}/run`, { chunkSize, maxChunks });
  }

  pauseTask(taskId: number): Observable<TaskInfo> {
    return this.http.post<TaskInfo>(`/api/tasks/${taskId}/pause`, null);
  }

  resumeTask(taskId: number): Observable<TaskInfo> {
    return this.http.post<TaskInfo>(`/api/tasks/${taskId}/resume`, null);
  }

  taskDetail(taskId: number): Observable<{ task: TaskInfo; events: unknown[]; commits: number }> {
    return this.http.get<{ task: TaskInfo; events: unknown[]; commits: number }>(`/api/tasks/${taskId}`);
  }

  publish(label: string, batchIds: number[]): Observable<TmVersion> {
    return this.http.post<TmVersion>('/api/versions/publish', { label, batchIds });
  }

  versions(): Observable<TmVersion[]> {
    return this.http.get<TmVersion[]>('/api/versions');
  }

  lineage(versionId: number): Observable<TmVersion[]> {
    return this.http.get<TmVersion[]>(`/api/versions/${versionId}/lineage`);
  }

  rollback(versionId: number, reason: string): Observable<TmVersion> {
    return this.http.post<TmVersion>(`/api/versions/${versionId}/rollback`, { reason });
  }
}
