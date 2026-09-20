import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Batch, Candidate, CandidateDetail, ConflictGroup, Lineage, MigrationTask, ReviewDecision, TmVersion
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  listBatches(): Observable<Batch[]> {
    return this.http.get<Batch[]>('/api/batches');
  }

  getBatch(id: number): Observable<Batch> {
    return this.http.get<Batch>(`/api/batches/${id}`);
  }

  candidatesOf(batchId: number): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(`/api/batches/${batchId}/candidates`);
  }

  candidateDetail(id: number): Observable<CandidateDetail> {
    return this.http.get<CandidateDetail>(`/api/candidates/${id}`);
  }

  decide(candidateId: number, reviewer: string, action: 'ACCEPT' | 'REJECT' | 'SET_FINAL',
         finalTarget: string | null, rationale: string | null, expectedVersion: number | null) {
    return this.http.post<Candidate>(`/api/candidates/${candidateId}/decisions`, {
      reviewer, action, finalTarget, rationale, expectedVersion
    });
  }

  decisionsOf(candidateId: number): Observable<ReviewDecision[]> {
    return this.http.get<ReviewDecision[]>(`/api/candidates/${candidateId}/decisions`);
  }

  conflictGroup(id: number): Observable<ConflictGroup> {
    return this.http.get<ConflictGroup>(`/api/conflicts/${id}`);
  }

  listTasks(): Observable<MigrationTask[]> {
    return this.http.get<MigrationTask[]>('/api/tasks');
  }

  createTask(batchId: number, name: string): Observable<MigrationTask> {
    return this.http.post<MigrationTask>('/api/tasks', { batchId, name });
  }

  startTask(id: number): Observable<MigrationTask> {
    return this.http.post<MigrationTask>(`/api/tasks/${id}/start`, {});
  }

  pauseTask(id: number): Observable<MigrationTask> {
    return this.http.post<MigrationTask>(`/api/tasks/${id}/pause`, {});
  }

  resumeTask(id: number): Observable<MigrationTask> {
    return this.http.post<MigrationTask>(`/api/tasks/${id}/resume`, {});
  }

  listVersions(): Observable<TmVersion[]> {
    return this.http.get<TmVersion[]>('/api/versions');
  }

  lineage(versionId: number): Observable<Lineage> {
    return this.http.get<Lineage>(`/api/versions/${versionId}/lineage`);
  }

  publish(label: string, batchIds: number[], actor: string): Observable<TmVersion> {
    return this.http.post<TmVersion>('/api/versions/publish', { label, batchIds, actor });
  }

  rollback(versionId: number, mode: 'REVERSE_VERSION' | 'POINTER', reason: string, actor: string) {
    return this.http.post<TmVersion>(`/api/versions/${versionId}/rollback`, { mode, reason, actor });
  }

  exportVersion(versionId: number) {
    return this.http.post(`/api/versions/${versionId}/export`, {});
  }

  downloadUrl(versionId: number): string {
    return `/api/versions/${versionId}/export`;
  }
}
