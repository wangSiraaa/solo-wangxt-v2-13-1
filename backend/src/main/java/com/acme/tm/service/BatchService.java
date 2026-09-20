package com.acme.tm.service;

import com.acme.tm.error.ApiException;
import com.acme.tm.model.*;
import com.acme.tm.repo.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BatchService {
    private final ImportBatchRepo batchRepo;
    private final BatchCandidateRepo candidateRepo;
    private final ConflictGroupRepo conflictRepo;
    private final TermMappingRuleRepo ruleRepo;
    private final LanguageProfileRepo profileRepo;
    private final UploadSessionRepo sessionRepo;
    private final UploadChunkRepo chunkRepo;

    public BatchService(ImportBatchRepo batchRepo, BatchCandidateRepo candidateRepo,
                        ConflictGroupRepo conflictRepo, TermMappingRuleRepo ruleRepo,
                        LanguageProfileRepo profileRepo, UploadSessionRepo sessionRepo,
                        UploadChunkRepo chunkRepo) {
        this.batchRepo = batchRepo;
        this.candidateRepo = candidateRepo;
        this.conflictRepo = conflictRepo;
        this.ruleRepo = ruleRepo;
        this.profileRepo = profileRepo;
        this.sessionRepo = sessionRepo;
        this.chunkRepo = chunkRepo;
    }

    /**
     * Idempotent batch import. The batch is bound to baseline version, language pair,
     * product line, vendor, TMX fingerprint and the term-mapping rules in force.
     * Retries with the same file return the existing batch; no duplicate batch or
     * duplicate candidates are ever created.
     */
    @Transactional
    public ImportBatch importTmx(String vendor, String sourceLang, String targetLang, String productLine,
                                 Long sourceVersionId, String tmxContent) {
        String fingerprint = Checksums.sha256(tmxContent);
        String key = String.join("|", vendor, sourceLang, targetLang, productLine, fingerprint);
        var existing = batchRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) return existing.get();

        List<TermMappingRule> rules = ruleRepo.findBySourceLangAndTargetLangAndProductLine(sourceLang, targetLang, productLine);
        ImportBatch batch = new ImportBatch(key, sourceVersionId, sourceLang, targetLang, productLine,
                vendor, fingerprint, snapshot(rules));
        try {
            batch = batchRepo.saveAndFlush(batch);
        } catch (DataIntegrityViolationException dup) {
            // concurrent import of the same file — the unique constraint guarantees a single batch
            return batchRepo.findByIdempotencyKey(key).orElseThrow(() -> dup);
        }

        int total = 0;
        for (TmxParser.Tu tu : TmxParser.parse(tmxContent)) {
            String src = tu.segmentsByLang().get(sourceLang);
            String tgt = tu.segmentsByLang().get(targetLang);
            if (src == null || tgt == null) continue;
            String sourceHash = Checksums.sha256(src);
            BatchCandidate c = new BatchCandidate(batch.getId(), sourceLang, targetLang, productLine,
                    src, tgt, sourceHash, Placeholders.signature(src));
            applyAnomalyChecks(c, rules);
            if (c.getStatus() != CandidateStatus.BLOCKED) {
                linkConflicts(c);
            }
            try {
                candidateRepo.saveAndFlush(c);
                total++;
            } catch (DataIntegrityViolationException dup) {
                // same source segment twice in one file (or replayed import): absorbed, not duplicated
            }
        }
        batch.setTotalCandidates(total);
        batch.setStatus(BatchStatus.REVIEWING);
        return batchRepo.save(batch);
    }

    /** Anomaly gate: placeholder integrity, language case rules, polysemous terms. Never guesses. */
    private void applyAnomalyChecks(BatchCandidate c, List<TermMappingRule> rules) {
        if (!Placeholders.preserved(c.getSourceText(), c.getTargetText())) {
            c.setStatus(CandidateStatus.BLOCKED);
            c.setAnomalyType(AnomalyType.PLACEHOLDER_MISMATCH);
            c.setAnomalyDetail("Placeholder count/name/order/escape differ. " +
                    Placeholders.diff(c.getSourceText(), c.getTargetText()));
            return;
        }
        CaseRule caseRule = profileRepo.findByCode(c.getTargetLang())
                .map(LanguageProfile::getCaseRule).orElse(CaseRule.PRESERVE);
        for (TermMappingRule r : rules) {
            if (!c.getSourceText().contains(r.getSourceTerm())) continue;
            if (r.isPolysemous()) {
                c.setStatus(CandidateStatus.BLOCKED);
                c.setAnomalyType(AnomalyType.POLYSEMOUS_TERM);
                c.setAnomalyDetail("Polysemous term '" + r.getSourceTerm() + "' requires a human choice among: "
                        + r.getAlternatives());
                return;
            }
            String expected = caseRule.apply(r.getApprovedTarget());
            if (containsIgnoreCase(c.getTargetText(), r.getApprovedTarget())
                    && !c.getTargetText().contains(expected)) {
                c.setStatus(CandidateStatus.BLOCKED);
                c.setAnomalyType(AnomalyType.CASE_VIOLATION);
                c.setAnomalyDetail("Term '" + r.getApprovedTarget() + "' must appear as '" + expected
                        + "' per case rule " + caseRule + " for language " + c.getTargetLang());
                return;
            }
        }
        c.setStatus(CandidateStatus.PENDING);
    }

    /** Same source + language pair + product line with a diverging target → traceable conflict state. */
    private void linkConflicts(BatchCandidate c) {
        List<BatchCandidate> others = candidateRepo.findSameSourceInOtherBatches(
                c.getSourceLang(), c.getTargetLang(), c.getProductLine(), c.getSourceHash(), c.getBatchId());
        List<BatchCandidate> diverging = new ArrayList<>();
        for (BatchCandidate o : others) {
            if (!o.getTargetText().equals(c.getTargetText())
                    && o.getStatus() != CandidateStatus.REJECTED
                    && o.getStatus() != CandidateStatus.BLOCKED) {
                diverging.add(o);
            }
        }
        if (diverging.isEmpty()) return;
        ConflictGroup group = conflictRepo
                .findBySourceHashAndSourceLangAndTargetLangAndProductLine(
                        c.getSourceHash(), c.getSourceLang(), c.getTargetLang(), c.getProductLine())
                .orElseGet(() -> conflictRepo.save(new ConflictGroup(
                        c.getSourceHash(), c.getSourceLang(), c.getTargetLang(), c.getProductLine())));
        c.setStatus(CandidateStatus.CONFLICT);
        c.setConflictGroupId(group.getId());
        for (BatchCandidate o : diverging) {
            if (o.getStatus() == CandidateStatus.PENDING) {
                o.setStatus(CandidateStatus.CONFLICT);
                o.setConflictGroupId(group.getId());
                candidateRepo.save(o);
            }
        }
    }

    // ---- chunked upload: out-of-order shards and duplicates are absorbed ----

    @Transactional
    public UploadSession initSession(String clientKey, String vendor, String sourceLang, String targetLang,
                                     String productLine, int totalChunks, String fingerprint) {
        return sessionRepo.findByClientKey(clientKey).orElseGet(() -> {
            try {
                return sessionRepo.saveAndFlush(new UploadSession(clientKey, vendor, sourceLang, targetLang,
                        productLine, totalChunks, fingerprint));
            } catch (DataIntegrityViolationException dup) {
                return sessionRepo.findByClientKey(clientKey).orElseThrow(() -> dup);
            }
        });
    }

    @Transactional
    public void addChunk(String clientKey, int index, String data) {
        UploadSession s = sessionRepo.findByClientKey(clientKey)
                .orElseThrow(() -> ApiException.notFound("upload session " + clientKey));
        if (index < 0 || index >= s.getTotalChunks()) {
            throw ApiException.badRequest("chunk index " + index + " out of range");
        }
        var existing = chunkRepo.findBySessionIdAndChunkIndex(s.getId(), index);
        if (existing.isPresent()) {
            if (!existing.get().getData().equals(data)) {
                throw ApiException.conflict("chunk " + index + " already uploaded with different content");
            }
            return; // duplicate shard absorbed
        }
        try {
            chunkRepo.saveAndFlush(new UploadChunk(s.getId(), index, data));
        } catch (DataIntegrityViolationException dup) {
            // concurrent duplicate shard — absorbed
        }
    }

    /** Assembles shards in index order regardless of arrival order; verifies the TMX fingerprint. */
    @Transactional
    public ImportBatch completeSession(String clientKey, Long sourceVersionId) {
        UploadSession s = sessionRepo.findByClientKey(clientKey)
                .orElseThrow(() -> ApiException.notFound("upload session " + clientKey));
        if ("COMPLETED".equals(s.getStatus()) && s.getBatchId() != null) {
            return batchRepo.findById(s.getBatchId())
                    .orElseThrow(() -> ApiException.notFound("batch " + s.getBatchId()));
        }
        List<UploadChunk> chunks = chunkRepo.findBySessionIdOrderByChunkIndexAsc(s.getId());
        if (chunks.size() != s.getTotalChunks()) {
            throw ApiException.badRequest("missing shards: got " + chunks.size() + " of " + s.getTotalChunks());
        }
        StringBuilder tmx = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.get(i).getChunkIndex() != i) {
                throw ApiException.badRequest("shard " + i + " missing");
            }
            tmx.append(chunks.get(i).getData());
        }
        String actual = Checksums.sha256(tmx.toString());
        if (!actual.equals(s.getFingerprint())) {
            throw ApiException.badRequest("fingerprint mismatch: expected " + s.getFingerprint() + " got " + actual);
        }
        ImportBatch batch = importTmx(s.getVendor(), s.getSourceLang(), s.getTargetLang(),
                s.getProductLine(), sourceVersionId, tmx.toString());
        s.setStatus("COMPLETED");
        s.setBatchId(batch.getId());
        sessionRepo.save(s);
        return batch;
    }

    public List<BatchCandidate> candidates(Long batchId) {
        return candidateRepo.findByBatchIdOrderByIdAsc(batchId);
    }

    public List<BatchCandidate> conflictChain(Long groupId) {
        return candidateRepo.findByConflictGroupIdOrderByIdAsc(groupId);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String snapshot(List<TermMappingRule> rules) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rules.size(); i++) {
            TermMappingRule r = rules.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(r.getId())
              .append(",\"sourceTerm\":\"").append(escape(r.getSourceTerm())).append("\"")
              .append(",\"approvedTarget\":\"").append(escape(r.getApprovedTarget())).append("\"")
              .append(",\"polysemous\":").append(r.isPolysemous()).append('}');
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
