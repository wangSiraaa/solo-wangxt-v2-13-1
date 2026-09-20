package com.acme.tm.dto;

import com.acme.tm.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    // ---- batches / import ----
    public record ImportRequest(@NotBlank String vendor, @NotBlank String sourceLang,
                                @NotBlank String targetLang, @NotBlank String productLine,
                                Long sourceVersionId, @NotBlank String tmx) {}

    public record BatchView(Long id, String idempotencyKey, Long sourceVersionId, String sourceLang,
                            String targetLang, String productLine, String vendor, String tmxFingerprint,
                            String mappingRules, BatchStatus status, int totalCandidates, Instant createdAt) {
        public static BatchView of(ImportBatch b) {
            return new BatchView(b.getId(), b.getIdempotencyKey(), b.getSourceVersionId(), b.getSourceLang(),
                    b.getTargetLang(), b.getProductLine(), b.getVendor(), b.getTmxFingerprint(),
                    b.getMappingRules(), b.getStatus(), b.getTotalCandidates(), b.getCreatedAt());
        }
    }

    public record CandidateView(Long id, Long batchId, String sourceLang, String targetLang, String productLine,
                                String sourceText, String targetText, CandidateStatus status,
                                String placeholderSig, String placeholderDiff,
                                AnomalyType anomalyType, String anomalyDetail,
                                Long conflictGroupId, String termOverride,
                                String decidedBy, Instant decidedAt, long version) {
        public static CandidateView of(BatchCandidate c) {
            return new CandidateView(c.getId(), c.getBatchId(), c.getSourceLang(), c.getTargetLang(),
                    c.getProductLine(), c.getSourceText(), c.getTargetText(), c.getStatus(),
                    c.getPlaceholderSig(),
                    com.acme.tm.service.Placeholders.diff(c.getSourceText(), c.getTargetText()),
                    c.getAnomalyType(), c.getAnomalyDetail(), c.getConflictGroupId(), c.getTermOverride(),
                    c.getDecidedBy(), c.getDecidedAt(), c.getVersion());
        }
    }

    // ---- chunked upload ----
    public record InitUploadRequest(@NotBlank String clientKey, @NotBlank String vendor,
                                    @NotBlank String sourceLang, @NotBlank String targetLang,
                                    @NotBlank String productLine, int totalChunks,
                                    @NotBlank String fingerprint) {}

    public record ChunkRequest(@NotBlank String data) {}

    public record CompleteUploadRequest(Long sourceVersionId) {}

    // ---- review ----
    public record DecisionRequest(@NotBlank String reviewer, @NotNull ReviewAction action,
                                  String termOverride, String correctedTarget, String reason,
                                  long expectedVersion) {}

    public record DecisionView(Long id, Long candidateId, String reviewer, ReviewAction action,
                               String termOverride, String reason, long priorVersion, Instant createdAt) {
        public static DecisionView of(ReviewDecision d) {
            return new DecisionView(d.getId(), d.getCandidateId(), d.getReviewer(), d.getAction(),
                    d.getTermOverride(), d.getReason(), d.getPriorVersion(), d.getCreatedAt());
        }
    }

    // ---- tasks ----
    public record TaskView(Long id, Long batchId, TaskStatus status, long checkpointCandidateId,
                           int processedCount, int committedCount, int skippedCount, String error,
                           Instant createdAt, Instant updatedAt) {
        public static TaskView of(MigrationTask t) {
            return new TaskView(t.getId(), t.getBatchId(), t.getStatus(), t.getCheckpointCandidateId(),
                    t.getProcessedCount(), t.getCommittedCount(), t.getSkippedCount(), t.getError(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record TaskEventView(Long id, String fromStatus, String toStatus, String message, Instant createdAt) {
        public static TaskEventView of(TaskEvent e) {
            return new TaskEventView(e.getId(), e.getFromStatus(), e.getToStatus(), e.getMessage(), e.getCreatedAt());
        }
    }

    public record RunRequest(Integer chunkSize, Integer maxChunks) {}

    // ---- versions ----
    public record PublishRequest(@NotBlank String label, @NotNull List<Long> batchIds) {}

    public record RollbackRequest(@NotBlank String reason) {}

    public record VersionView(Long id, Long parentId, String label, VersionStatus status, boolean effective,
                              String checksum, String batchManifest, String conflictReport,
                              String rollbackReason, String downloadUrl, Instant createdAt) {
        public static VersionView of(TmVersion v) {
            return new VersionView(v.getId(), v.getParentId(), v.getLabel(), v.getStatus(), v.isEffective(),
                    v.getChecksum(), v.getBatchManifest(), v.getConflictReport(), v.getRollbackReason(),
                    v.getDownloadUrl(), v.getCreatedAt());
        }
    }

    // ---- legacy ----
    public record LegacyImportRequest(@NotBlank String vendor, @NotBlank String sourceLang,
                                      @NotBlank String targetLang, @NotBlank String productLine,
                                      @NotBlank String label, @NotBlank String tmx) {}

    public record ReplaceRequest(@NotBlank String text, @NotBlank String sourceLang,
                                 @NotBlank String targetLang, @NotBlank String productLine) {}

    public record ReplaceResponse(String result) {}

    // ---- config ----
    public record ProfileRequest(@NotBlank String code, @NotNull CaseRule caseRule) {}

    public record RuleRequest(@NotBlank String sourceLang, @NotBlank String targetLang,
                              @NotBlank String productLine, @NotBlank String sourceTerm,
                              @NotBlank String approvedTarget, String replacedTarget,
                              boolean polysemous, String alternatives, String note) {}
}
