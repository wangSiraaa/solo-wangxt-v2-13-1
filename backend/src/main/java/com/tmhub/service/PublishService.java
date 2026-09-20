package com.tmhub.service;

import com.tmhub.domain.Batch;
import com.tmhub.domain.BatchStatus;
import com.tmhub.domain.Candidate;
import com.tmhub.domain.CandidateStatus;
import com.tmhub.domain.ConflictGroup;
import com.tmhub.domain.EntryOrigin;
import com.tmhub.domain.TmEntry;
import com.tmhub.domain.TmVersion;
import com.tmhub.domain.VersionBatch;
import com.tmhub.domain.VersionConflictResolution;
import com.tmhub.domain.VersionKind;
import com.tmhub.domain.VersionPointer;
import com.tmhub.domain.VersionPointerHistory;
import com.tmhub.domain.VersionStatus;
import com.tmhub.repo.BatchRepository;
import com.tmhub.repo.CandidateRepository;
import com.tmhub.repo.ConflictGroupRepository;
import com.tmhub.repo.MigrationCommitRepository;
import com.tmhub.repo.TmEntryRepository;
import com.tmhub.repo.TmVersionRepository;
import com.tmhub.repo.VersionBatchRepository;
import com.tmhub.repo.VersionConflictResolutionRepository;
import com.tmhub.repo.VersionPointerHistoryRepository;
import com.tmhub.repo.VersionPointerRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Release and rollback.
 *
 * Publishing materializes an immutable version: parent pointer, batch manifest, conflict-resolution
 * record and a content checksum. Rollback never edits or deletes a published version — it either
 * creates a reverse version (with a mandatory reason) or only moves the effective pointer.
 */
@Service
public class PublishService {

    private final TmVersionRepository versionRepository;
    private final TmEntryRepository entryRepository;
    private final BatchRepository batchRepository;
    private final CandidateRepository candidateRepository;
    private final MigrationCommitRepository commitRepository;
    private final ConflictGroupRepository conflictGroupRepository;
    private final VersionBatchRepository versionBatchRepository;
    private final VersionConflictResolutionRepository versionConflictRepository;
    private final VersionPointerRepository pointerRepository;
    private final VersionPointerHistoryRepository pointerHistoryRepository;

    public PublishService(TmVersionRepository versionRepository, TmEntryRepository entryRepository,
                          BatchRepository batchRepository, CandidateRepository candidateRepository,
                          MigrationCommitRepository commitRepository,
                          ConflictGroupRepository conflictGroupRepository,
                          VersionBatchRepository versionBatchRepository,
                          VersionConflictResolutionRepository versionConflictRepository,
                          VersionPointerRepository pointerRepository,
                          VersionPointerHistoryRepository pointerHistoryRepository) {
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.batchRepository = batchRepository;
        this.candidateRepository = candidateRepository;
        this.commitRepository = commitRepository;
        this.conflictGroupRepository = conflictGroupRepository;
        this.versionBatchRepository = versionBatchRepository;
        this.versionConflictRepository = versionConflictRepository;
        this.pointerRepository = pointerRepository;
        this.pointerHistoryRepository = pointerHistoryRepository;
    }

    @Transactional
    public TmVersion publish(String label, List<Long> batchIds, String actor) {
        if (batchIds == null || batchIds.isEmpty()) {
            throw new BadRequestException("at least one batch is required");
        }
        List<Batch> batches = new ArrayList<>();
        for (Long batchId : batchIds) {
            Batch batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new NotFoundException("batch " + batchId));
            if (batch.getStatus() != BatchStatus.READY) {
                throw new BadRequestException("batch " + batchId + " is " + batch.getStatus() + ", not READY");
            }
            batches.add(batch);
        }

        // Quarantined anomalies and open conflicts block the release; nothing is auto-guessed.
        List<Candidate> blocked = candidateRepository.findByBatchIdInAndStatus(batchIds, CandidateStatus.BLOCKED);
        if (!blocked.isEmpty()) {
            throw new PublishBlockedException("release blocked: " + blocked.size()
                    + " quarantined candidates need manual handling, e.g. candidate " + blocked.get(0).getId());
        }
        List<Candidate> inConflict = candidateRepository.findByBatchIdInAndStatus(batchIds, CandidateStatus.CONFLICT);
        if (!inConflict.isEmpty()) {
            throw new PublishBlockedException("release blocked: " + inConflict.size()
                    + " unresolved conflicts, e.g. candidate " + inConflict.get(0).getId());
        }

        TmVersion parent = currentPointerVersion();
        Map<String, TmEntry> entries = new LinkedHashMap<>();
        if (parent != null) {
            for (TmEntry entry : entryRepository.findByVersionIdOrderById(parent.getId())) {
                entries.put(entry.getIdentityKey(), copyOf(entry));
            }
        }

        // Apply committed candidates (accepted replacements) in candidate-id order.
        List<Candidate> committed = new ArrayList<>();
        for (Long batchId : batchIds) {
            for (Candidate candidate : candidateRepository.findByBatchIdOrderById(batchId)) {
                if (candidate.isCommitted()) {
                    committed.add(candidate);
                }
            }
        }
        committed.sort(Comparator.comparing(Candidate::getId));
        for (Candidate candidate : committed) {
            TmEntry entry = new TmEntry();
            entry.setSourceLang(candidate.getSourceLang());
            entry.setTargetLang(candidate.getTargetLang());
            entry.setProductLine(candidate.getProductLine());
            entry.setSourceText(candidate.getSourceText());
            entry.setTargetText(candidate.effectiveTarget());
            entry.setIdentityKey(candidate.getIdentityKey());
            entry.setOrigin(EntryOrigin.BATCH_MIGRATION);
            entry.setOriginCandidateId(candidate.getId());
            entries.put(candidate.getIdentityKey(), entry);
        }

        TmVersion version = new TmVersion();
        version.setLabel(label);
        version.setKind(VersionKind.INCREMENTAL);
        version.setParentId(parent != null ? parent.getId() : null);
        version.setStatus(VersionStatus.PUBLISHED);
        version.setCreatedBy(actor);
        version = versionRepository.saveAndFlush(version);

        persistEntries(version.getId(), entries.values());
        version.setChecksum(computeChecksum(version.getId()));
        versionRepository.save(version);

        for (Batch batch : batches) {
            VersionBatch link = new VersionBatch();
            link.setVersionId(version.getId());
            link.setBatchId(batch.getId());
            versionBatchRepository.save(link);
        }
        recordConflictResolutions(version.getId(), committed);
        movePointer(version, actor, "publish " + label);
        return version;
    }

    private void recordConflictResolutions(Long versionId, List<Candidate> committed) {
        List<Long> groupIds = committed.stream()
                .map(Candidate::getConflictGroupId).filter(java.util.Objects::nonNull).distinct().toList();
        for (Long groupId : groupIds) {
            ConflictGroup group = conflictGroupRepository.findById(groupId).orElseThrow();
            VersionConflictResolution record = new VersionConflictResolution();
            record.setVersionId(versionId);
            record.setConflictGroupId(group.getId());
            record.setResolvedCandidateId(group.getResolvedCandidateId());
            record.setNote(group.getResolutionNote());
            record.setResolvedBy(group.getResolvedBy());
            record.setResolvedAt(group.getResolvedAt());
            versionConflictRepository.save(record);
        }
    }

    public enum RollbackMode { REVERSE_VERSION, POINTER }

    /**
     * Roll back the effective version. REVERSE_VERSION creates a new immutable version whose content
     * equals the pre-release state; POINTER only moves the effective pointer back. Both need a reason.
     */
    @Transactional
    public TmVersion rollback(long versionId, RollbackMode mode, String reason, String actor) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("a rollback reason is required");
        }
        TmVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("version " + versionId));
        TmVersion pointer = currentPointerVersion();
        if (pointer == null || !pointer.getId().equals(versionId)) {
            throw new BadRequestException("only the effective version can be rolled back");
        }
        TmVersion parent = version.getParentId() != null
                ? versionRepository.findById(version.getParentId()).orElse(null) : null;

        if (mode == RollbackMode.POINTER) {
            if (parent == null) {
                throw new BadRequestException("version " + versionId + " has no parent to point back to");
            }
            movePointer(parent, actor, "rollback of version " + versionId + ": " + reason);
            return parent;
        }

        TmVersion reverse = new TmVersion();
        reverse.setLabel("rollback-of-" + version.getLabel());
        reverse.setKind(VersionKind.ROLLBACK);
        reverse.setParentId(version.getId());
        reverse.setStatus(VersionStatus.PUBLISHED);
        reverse.setReason(reason);
        reverse.setCreatedBy(actor);
        reverse = versionRepository.saveAndFlush(reverse);

        if (parent != null) {
            List<TmEntry> restored = new ArrayList<>();
            for (TmEntry entry : entryRepository.findByVersionIdOrderById(parent.getId())) {
                TmEntry copy = copyOf(entry);
                copy.setOrigin(EntryOrigin.ROLLBACK);
                restored.add(copy);
            }
            persistEntries(reverse.getId(), restored);
        }
        reverse.setChecksum(computeChecksum(reverse.getId()));
        versionRepository.save(reverse);
        movePointer(reverse, actor, "rollback of version " + versionId + ": " + reason);
        return reverse;
    }

    @Transactional
    public TmVersion createBaseline(String label, String actor) {
        TmVersion baseline = new TmVersion();
        baseline.setLabel(label);
        baseline.setKind(VersionKind.BASELINE);
        baseline.setStatus(VersionStatus.PUBLISHED);
        baseline.setCreatedBy(actor);
        baseline = versionRepository.saveAndFlush(baseline);
        baseline.setChecksum(computeChecksum(baseline.getId()));
        versionRepository.save(baseline);
        movePointer(baseline, actor, "baseline");
        return baseline;
    }

    public TmVersion currentPointerVersion() {
        return pointerRepository.findById((short) 1)
                .map(p -> versionRepository.findById(p.getVersionId()).orElse(null))
                .orElse(null);
    }

    /** Move the effective pointer to an already-published version (used by the legacy flows). */
    @Transactional
    public void publishPointerOnly(TmVersion target, String actor, String note) {
        movePointer(target, actor, note);
    }

    private void movePointer(TmVersion target, String actor, String note) {
        VersionPointer pointer = pointerRepository.findById((short) 1).orElseGet(() -> {
            VersionPointer p = new VersionPointer();
            p.setId((short) 1);
            p.setVersionId(target.getId());
            return p;
        });
        Long from = pointer.getVersionId();
        pointer.setVersionId(target.getId());
        pointer.setMovedAt(OffsetDateTime.now());
        pointer.setMovedBy(actor);
        pointer.setNote(note);
        pointerRepository.save(pointer);

        VersionPointerHistory history = new VersionPointerHistory();
        history.setFromVersion(from);
        history.setToVersion(target.getId());
        history.setMovedBy(actor);
        history.setNote(note);
        pointerHistoryRepository.save(history);
    }

    void persistEntries(Long versionId, Iterable<TmEntry> entries) {
        for (TmEntry entry : entries) {
            entry.setId(null);
            entry.setVersionId(versionId);
            entry.setCreatedAt(OffsetDateTime.now());
            entryRepository.save(entry);
        }
    }

    static TmEntry copyOf(TmEntry source) {
        TmEntry copy = new TmEntry();
        copy.setSourceLang(source.getSourceLang());
        copy.setTargetLang(source.getTargetLang());
        copy.setProductLine(source.getProductLine());
        copy.setSourceText(source.getSourceText());
        copy.setTargetText(source.getTargetText());
        copy.setIdentityKey(source.getIdentityKey());
        copy.setOrigin(source.getOrigin());
        copy.setOriginCandidateId(source.getOriginCandidateId());
        return copy;
    }

    /** Deterministic content checksum: sha256 over entries sorted by identity key. */
    public String computeChecksum(long versionId) {
        List<TmEntry> entries = entryRepository.findByVersionIdOrderById(versionId);
        StringBuilder canonical = new StringBuilder();
        entries.stream()
                .sorted(Comparator.comparing(TmEntry::getIdentityKey))
                .forEach(e -> canonical.append(e.getIdentityKey()).append('\t')
                        .append(e.getTargetText()).append('\n'));
        return Hashes.sha256(canonical.toString());
    }
}
