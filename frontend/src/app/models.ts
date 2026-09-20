export interface Batch {
  id: number;
  idempotencyKey: string;
  sourceVersionId: number | null;
  sourceLang: string;
  targetLang: string;
  productLine: string;
  vendor: string;
  tmxFingerprint: string;
  mappingRules: string;
  status: string;
  totalCandidates: number;
  createdAt: string;
}

export interface Candidate {
  id: number;
  batchId: number;
  sourceLang: string;
  targetLang: string;
  productLine: string;
  sourceText: string;
  targetText: string;
  status: 'PENDING' | 'CONFLICT' | 'BLOCKED' | 'ACCEPTED' | 'REJECTED' | 'MIGRATED';
  placeholderSig: string;
  placeholderDiff: string;
  anomalyType: 'PLACEHOLDER_MISMATCH' | 'CASE_VIOLATION' | 'POLYSEMOUS_TERM' | null;
  anomalyDetail: string | null;
  conflictGroupId: number | null;
  termOverride: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  version: number;
}

export interface Decision {
  id: number;
  candidateId: number;
  reviewer: string;
  action: string;
  termOverride: string | null;
  reason: string | null;
  priorVersion: number;
  createdAt: string;
}

export interface TaskInfo {
  id: number;
  batchId: number;
  status: string;
  checkpointCandidateId: number;
  processedCount: number;
  committedCount: number;
  skippedCount: number;
  error: string | null;
}

export interface TaskEvent {
  id: number;
  fromStatus: string | null;
  toStatus: string;
  message: string | null;
  createdAt: string;
}

export interface TmVersion {
  id: number;
  parentId: number | null;
  label: string;
  status: string;
  effective: boolean;
  checksum: string;
  batchManifest: string;
  conflictReport: string;
  rollbackReason: string | null;
  downloadUrl: string;
  createdAt: string;
}
