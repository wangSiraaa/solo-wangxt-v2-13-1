package com.acme.tm.service;

import com.acme.tm.error.ApiException;
import com.acme.tm.error.BlockedPublishException;
import com.acme.tm.model.*;
import com.acme.tm.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Publishing and rollback. Publishing folds the committed candidates of the given batches
 * into a NEW version carrying parent pointer, batch manifest, conflict-handling records and
 * a content checksum. Published versions are immutable: rollback only ever creates a reverse
 * version (with a mandatory reason) and moves the effective pointer — nothing is deleted or
 * rewritten, and old download URLs stay valid.
 */
@Service
public class PublishService {
    private final TmVersionRepo versionRepo;
    private final TmEntryRepo entryRepo;
    private final ImportBatchRepo batchRepo;
    private final BatchCandidateRepo candidateRepo;
    private final MigrationCommitRepo commitRepo;
    private final ReviewDecisionRepo decisionRepo;

    public PublishService(TmVersionRepo versionRepo, TmEntryRepo entryRepo, ImportBatchRepo batchRepo,
                          BatchCandidateRepo candidateRepo, MigrationCommitRepo commitRepo,
                          ReviewDecisionRepo decisionRepo) {
        this.versionRepo = versionRepo;
        this.entryRepo = entryRepo;
        this.batchRepo = batchRepo;
        this.candidateRepo = candidateRepo;
        this.commitRepo = commitRepo;
        this.decisionRepo = decisionRepo;
    }

    @Transactional
    public TmVersion publish(String label, List<Long> batchIds) {
        List<String> blockers = new ArrayList<>();
        List<ImportBatch> batches = new ArrayList<>();
        for (Long batchId : batchIds) {
            ImportBatch b = batchRepo.findById(batchId)
                    .orElseThrow(() -> ApiException.notFound("batch " + batchId));
            batches.add(b);
            for (BatchCandidate c : candidateRepo.findByBatchIdAndStatus(batchId, CandidateStatus.BLOCKED)) {
                blockers.add("candidate " + c.getId() + " BLOCKED (" + c.getAnomalyType() + "): " + c.getAnomalyDetail());
            }
            for (BatchCandidate c : candidateRepo.findByBatchIdAndStatus(batchId, CandidateStatus.CONFLICT)) {
                blockers.add("candidate " + c.getId() + " in unresolved conflict group " + c.getConflictGroupId());
            }
        }
        if (!blockers.isEmpty()) throw new BlockedPublishException(blockers);

        TmVersion parent = versionRepo.findByEffectiveTrue().orElse(null);

        // content = parent entries + committed candidates of the batches (upsert by source key)
        Map<String, TmEntry> content = new LinkedHashMap<>();
        if (parent != null) {
            for (TmEntry e : entryRepo.findByVersionIdOrderByIdAsc(parent.getId())) {
                content.put(keyOf(e.getSourceLang(), e.getTargetLang(), e.getProductLine(), e.getSourceText()),
                        new TmEntry(0L, e.getSourceLang(), e.getTargetLang(), e.getProductLine(),
                                e.getSourceText(), e.getTargetText(), e.getOriginBatchId(), e.getOriginCandidateId()));
            }
        }
        List<Long> committedCandidateIds = new ArrayList<>();
        for (ImportBatch b : batches) {
            for (MigrationCommit commit : commitRepo.findByBatchId(b.getId())) {
                BatchCandidate c = candidateRepo.findById(commit.getCandidateId()).orElseThrow();
                committedCandidateIds.add(c.getId());
                content.put(keyOf(c.getSourceLang(), c.getTargetLang(), c.getProductLine(), c.getSourceText()),
                        new TmEntry(0L, c.getSourceLang(), c.getTargetLang(), c.getProductLine(),
                                c.getSourceText(), c.getTargetText(), b.getId(), c.getId()));
            }
        }

        TmVersion version = new TmVersion(parent == null ? null : parent.getId(), label, VersionStatus.PUBLISHED,
                checksumOf(content.values()));
        version.setBatchManifest(manifest(batches));
        version.setConflictReport(conflictReport(batches));
        version.setEffective(true);
        version = versionRepo.save(version);
        version.setDownloadUrl("/api/versions/" + version.getId() + "/tmx");
        version = versionRepo.save(version);

        final Long newVersionId = version.getId();
        for (TmEntry e : content.values()) {
            entryRepo.save(new TmEntry(newVersionId, e.getSourceLang(), e.getTargetLang(), e.getProductLine(),
                    e.getSourceText(), e.getTargetText(), e.getOriginBatchId(), e.getOriginCandidateId()));
        }
        if (parent != null) {
            parent.setEffective(false);
            if (parent.getStatus() == VersionStatus.PUBLISHED) parent.setStatus(VersionStatus.SUPERSEDED);
            versionRepo.save(parent);
        }
        return version;
    }

    /**
     * Rollback = a new reverse version whose content is copied from the target version,
     * linked to the current effective version as its parent, with a mandatory reason.
     * The effective pointer moves; history is never deleted or rewritten.
     */
    @Transactional
    public TmVersion rollback(Long targetVersionId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("rollback requires a reason");
        }
        TmVersion target = versionRepo.findById(targetVersionId)
                .orElseThrow(() -> ApiException.notFound("version " + targetVersionId));
        TmVersion current = versionRepo.findByEffectiveTrue()
                .orElseThrow(() -> ApiException.conflict("no effective version to roll back from"));

        List<TmEntry> targetEntries = entryRepo.findByVersionIdOrderByIdAsc(target.getId());
        TmVersion reverse = new TmVersion(current.getId(),
                "rollback-to-v" + target.getId(), VersionStatus.ROLLBACK, checksumOf(targetEntries));
        reverse.setRollbackReason(reason);
        reverse.setEffective(true);
        reverse = versionRepo.save(reverse);
        reverse.setDownloadUrl("/api/versions/" + reverse.getId() + "/tmx");
        reverse = versionRepo.save(reverse);
        for (TmEntry e : targetEntries) {
            entryRepo.save(new TmEntry(reverse.getId(), e.getSourceLang(), e.getTargetLang(), e.getProductLine(),
                    e.getSourceText(), e.getTargetText(), e.getOriginBatchId(), e.getOriginCandidateId()));
        }
        current.setEffective(false);
        if (current.getStatus() == VersionStatus.PUBLISHED) current.setStatus(VersionStatus.SUPERSEDED);
        versionRepo.save(current);
        return reverse;
    }

    /** Lineage chain from the given version back to the root. */
    public List<TmVersion> lineage(Long versionId) {
        List<TmVersion> chain = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Long cursor = versionId;
        while (cursor != null && seen.add(cursor)) {
            final Long id = cursor;
            TmVersion v = versionRepo.findById(id)
                    .orElseThrow(() -> ApiException.notFound("version " + id));
            chain.add(v);
            cursor = v.getParentId();
        }
        return chain;
    }

    /** Deterministic TMX export — identical content always yields identical bytes/checksum. */
    public String exportTmx(Long versionId) {
        TmVersion v = versionRepo.findById(versionId)
                .orElseThrow(() -> ApiException.notFound("version " + versionId));
        List<TmEntry> entries = entryRepo.findByVersionIdOrderByIdAsc(versionId);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
          .append("<tmx version=\"1.4\">\n  <header creationtool=\"tm-platform\" segtype=\"sentence\"/>\n  <body>\n");
        for (TmEntry e : entries) {
            sb.append("    <tu>\n")
              .append("      <tuv xml:lang=\"").append(esc(e.getSourceLang())).append("\"><seg>")
              .append(esc(e.getSourceText())).append("</seg></tuv>\n")
              .append("      <tuv xml:lang=\"").append(esc(e.getTargetLang())).append("\"><seg>")
              .append(esc(e.getTargetText())).append("</seg></tuv>\n")
              .append("    </tu>\n");
        }
        sb.append("  </body>\n</tmx>\n");
        return sb.toString();
    }

    public String checksumOf(Collection<TmEntry> entries) {
        String canonical = entries.stream()
                .map(e -> keyOf(e.getSourceLang(), e.getTargetLang(), e.getProductLine(), e.getSourceText())
                        + "=" + e.getTargetText())
                .sorted()
                .collect(Collectors.joining("\n"));
        return Checksums.sha256(canonical);
    }

    private String manifest(List<ImportBatch> batches) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < batches.size(); i++) {
            ImportBatch b = batches.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"batchId\":").append(b.getId())
              .append(",\"vendor\":\"").append(b.getVendor()).append("\"")
              .append(",\"sourceLang\":\"").append(b.getSourceLang()).append("\"")
              .append(",\"targetLang\":\"").append(b.getTargetLang()).append("\"")
              .append(",\"productLine\":\"").append(b.getProductLine()).append("\"")
              .append(",\"tmxFingerprint\":\"").append(b.getTmxFingerprint()).append("\"")
              .append(",\"sourceVersionId\":").append(b.getSourceVersionId() == null ? "null" : b.getSourceVersionId())
              .append('}');
        }
        return sb.append(']').toString();
    }

    private String conflictReport(List<ImportBatch> batches) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ImportBatch b : batches) {
            List<Long> ids = candidateRepo.findByBatchIdOrderByIdAsc(b.getId()).stream()
                    .filter(c -> c.getConflictGroupId() != null).map(BatchCandidate::getId).toList();
            if (ids.isEmpty()) continue;
            for (ReviewDecision d : decisionRepo.findByCandidateIdIn(ids)) {
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"candidateId\":").append(d.getCandidateId())
                  .append(",\"reviewer\":\"").append(d.getReviewer()).append("\"")
                  .append(",\"action\":\"").append(d.getAction()).append("\"")
                  .append(",\"reason\":\"").append(d.getReason() == null ? "" : d.getReason().replace("\"", "'")).append("\"")
                  .append(",\"decidedAt\":\"").append(d.getCreatedAt()).append("\"")
                  .append('}');
            }
        }
        return sb.append(']').toString();
    }

    private static String keyOf(String sl, String tl, String pl, String src) {
        return sl + "|" + tl + "|" + pl + "|" + src;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
