export interface Batch {
  id: number;
  idempotencyKey: string;
  sourceVersionId: number;
  sourceLang: string;
  targetLang: string;
  productLine: string;
  vendor: string;
  tmxFingerprint: string;
  termMappingRuleId: number | null;
  expectedShards: number;
  receivedShards: number;
  status: 'RECEIVING' | 'READY' | 'FAILED';
  error: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Candidate {
  id: number;
  batchId: number;
  conflictGroupId: number | null;
  sourceLang: string;
  targetLang: string;
  productLine: string;
  vendor: string;
  sourceText: string;
  proposedTarget: string;
  finalTarget: string | null;
  identityKey: string;
  proposalKey: string;
  status: 'PENDING' | 'CONFLICT' | 'ACCEPTED' | 'REJECTED' | 'BLOCKED';
  committed: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CandidateAnomaly {
  id: number;
  candidateId: number;
  type: 'PLACEHOLDER_MISMATCH' | 'CASING_VIOLATION' | 'POLYSEMY';
  detail: string;
  resolved: boolean;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolution: string | null;
}

export interface ReviewDecision {
  id: number;
  candidateId: number;
  reviewer: string;
  action: 'ACCEPT' | 'REJECT' | 'SET_FINAL' | 'AUTO_SUPERSEDE';
  finalTarget: string | null;
  rationale: string | null;
  previousStatus: string;
  newStatus: string;
  previousFinalTarget: string | null;
  decidedAt: string;
}

export interface PlaceholderDiff {
  sourceTokens: string[];
  targetTokens: string[];
  escapeSource: string[];
  escapeTarget: string[];
}

export interface CandidateDetail {
  candidate: Candidate;
  anomalies: CandidateAnomaly[];
  decisions: ReviewDecision[];
  placeholderDiff: PlaceholderDiff;
  conflictChain: Candidate[];
}

export interface MigrationTask {
  id: number;
  name: string;
  batchId: number;
  status: 'PENDING' | 'RUNNING' | 'PAUSED' | 'FAILED' | 'INTERRUPTED' | 'COMPLETED';
  total: number;
  committedCount: number;
  lastCandidateId: number;
  error: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TmVersion {
  id: number;
  label: string;
  kind: 'BASELINE' | 'LEGACY_IMPORT' | 'INCREMENTAL' | 'ROLLBACK';
  parentId: number | null;
  status: string;
  checksum: string | null;
  reason: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface ConflictResolutionRecord {
  id: number;
  versionId: number;
  conflictGroupId: number;
  resolvedCandidateId: number | null;
  note: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
}

export interface VersionNode {
  version: TmVersion;
  batchIds: number[];
  conflictResolutions: ConflictResolutionRecord[];
  effective: boolean;
}

export interface Lineage {
  chain: VersionNode[];
  effectiveVersionId: number | null;
}

export interface ConflictGroup {
  id: number;
  identityKey: string;
  status: 'OPEN' | 'RESOLVED';
  resolvedCandidateId: number | null;
  resolutionNote: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
}
