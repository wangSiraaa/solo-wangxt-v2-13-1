package com.tmhub.service;

import com.tmhub.domain.AnomalyType;
import com.tmhub.domain.Batch;
import com.tmhub.domain.BatchShard;
import com.tmhub.domain.BatchStatus;
import com.tmhub.domain.Candidate;
import com.tmhub.domain.CandidateAnomaly;
import com.tmhub.domain.CandidateStatus;
import com.tmhub.domain.ConflictGroup;
import com.tmhub.repo.BatchRepository;
import com.tmhub.repo.BatchShardRepository;
import com.tmhub.repo.CandidateAnomalyRepository;
import com.tmhub.repo.CandidateRepository;
import com.tmhub.repo.ConflictGroupRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Incremental batch intake.
 *
 * Idempotency contract:
 *  - a batch is bound to (tmx fingerprint, source version, language pair, product line, vendor);
 *    re-initiating the same delivery returns the existing batch instead of creating a new one;
 *  - shards are stored under (batch_id, shard_index), so re-uploaded or out-of-order shards
 *    never duplicate; the batch is assembled only when every shard is present;
 *  - candidate generation dedupes on proposal_key, so replays never create duplicate candidates;
 *  - candidates that already carry a human decision are never reset by a replay.
 */
@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final BatchShardRepository shardRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateAnomalyRepository anomalyRepository;
    private final ConflictGroupRepository conflictGroupRepository;
    private final TmxParser tmxParser;
    private final ValidationService validationService;

    public BatchService(BatchRepository batchRepository,
                        BatchShardRepository shardRepository,
                        CandidateRepository candidateRepository,
                        CandidateAnomalyRepository anomalyRepository,
                        ConflictGroupRepository conflictGroupRepository,
                        TmxParser tmxParser,
                        ValidationService validationService) {
        this.batchRepository = batchRepository;
        this.shardRepository = shardRepository;
        this.candidateRepository = candidateRepository;
        this.anomalyRepository = anomalyRepository;
        this.conflictGroupRepository = conflictGroupRepository;
        this.tmxParser = tmxParser;
        this.validationService = validationService;
    }

    public record InitiateCommand(long sourceVersionId, String sourceLang, String targetLang,
                                  String productLine, String vendor, String tmxFingerprint,
                                  Long termMappingRuleId, String mappingRulesSnapshot,
                                  int expectedShards) {}

    /** Get-or-create by idempotency key; concurrent retries collapse onto one row. */
    @Transactional
    public Batch initiate(InitiateCommand cmd) {
        if (cmd.expectedShards() < 1) {
            throw new BadRequestException("expectedShards must be >= 1");
        }
        String key = Hashes.batchKey(cmd.tmxFingerprint(), cmd.sourceVersionId(), cmd.sourceLang(),
                cmd.targetLang(), cmd.productLine(), cmd.vendor());
        return batchRepository.findByIdempotencyKey(key).orElseGet(() -> {
            Batch batch = new Batch();
            batch.setIdempotencyKey(key);
            batch.setSourceVersionId(cmd.sourceVersionId());
            batch.setSourceLang(cmd.sourceLang());
            batch.setTargetLang(cmd.targetLang());
            batch.setProductLine(cmd.productLine());
            batch.setVendor(cmd.vendor());
            batch.setTmxFingerprint(cmd.tmxFingerprint());
            batch.setTermMappingRuleId(cmd.termMappingRuleId());
            batch.setMappingRulesSnapshot(cmd.mappingRulesSnapshot());
            batch.setExpectedShards(cmd.expectedShards());
            try {
                return batchRepository.saveAndFlush(batch);
            } catch (DataIntegrityViolationException race) {
                // Another retry won the insert race; return the canonical row.
                return batchRepository.findByIdempotencyKey(key).orElseThrow();
            }
        });
    }

    /**
     * Store one shard and, when all shards have arrived, assemble, verify and generate candidates.
     * Re-uploading the same shard index is a no-op; out-of-order arrival is fine.
     */
    @Transactional
    public Batch uploadShard(long batchId, int shardIndex, byte[] content) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("batch " + batchId));
        if (batch.getStatus() == BatchStatus.READY) {
            return batch; // replay after completion: nothing to do
        }
        if (batch.getStatus() == BatchStatus.FAILED) {
            batch.setStatus(BatchStatus.RECEIVING); // allow retry after a transient failure
            batch.setError(null);
        }
        if (shardIndex < 0 || shardIndex >= batch.getExpectedShards()) {
            throw new BadRequestException("shard index " + shardIndex + " out of range");
        }
        String shardFingerprint = Hashes.sha256(content);
        boolean exists = shardRepository.findById(new BatchShard.BatchShardId(batchId, shardIndex)).isPresent();
        if (!exists) {
            BatchShard shard = new BatchShard();
            shard.setBatchId(batchId);
            shard.setShardIndex(shardIndex);
            shard.setShardFingerprint(shardFingerprint);
            shard.setContent(content);
            try {
                shardRepository.saveAndFlush(shard);
            } catch (DataIntegrityViolationException race) {
                // concurrent duplicate shard upload: ignore
            }
        }
        long received = shardRepository.countByBatchId(batchId);
        batch.setReceivedShards((int) received);
        batch.setUpdatedAt(java.time.OffsetDateTime.now());
        if (received == batch.getExpectedShards()) {
            finalizeBatch(batch);
        }
        return batchRepository.save(batch);
    }

    private void finalizeBatch(Batch batch) {
        List<BatchShard> shards = shardRepository.findByBatchIdOrderByShardIndex(batch.getId());
        ByteArrayOutputStream assembled = new ByteArrayOutputStream();
        for (BatchShard shard : shards) {
            assembled.writeBytes(shard.getContent());
        }
        byte[] content = assembled.toByteArray();
        String fingerprint = Hashes.sha256(content);
        if (!fingerprint.equals(batch.getTmxFingerprint())) {
            batch.setStatus(BatchStatus.FAILED);
            batch.setError("assembled fingerprint " + fingerprint
                    + " does not match declared " + batch.getTmxFingerprint());
            return;
        }
        try {
            List<TmxParser.Tu> tus = tmxParser.parse(content);
            generateCandidates(batch, tus);
            batch.setStatus(BatchStatus.READY);
        } catch (RuntimeException e) {
            batch.setStatus(BatchStatus.FAILED);
            batch.setError(e.getMessage());
        }
    }

    /**
     * Replay-safe candidate generation. Existing proposals are skipped; a differing proposal for an
     * already-decided identity is quarantined into a conflict instead of touching the decision.
     */
    private void generateCandidates(Batch batch, List<TmxParser.Tu> tus) {
        String groupScope = String.valueOf(batch.getSourceVersionId());
        for (TmxParser.Tu tu : tus) {
            String sourceLang = tu.sourceLang() != null ? tu.sourceLang() : batch.getSourceLang();
            String targetLang = tu.targetLang() != null ? tu.targetLang() : batch.getTargetLang();
            String identityKey = Hashes.identityKey(sourceLang, targetLang, batch.getProductLine(), tu.source());
            String groupKey = Hashes.sha256(identityKey + "|" + groupScope);
            String proposalKey = Hashes.proposalKey(groupKey, tu.target());

            if (candidateRepository.findByProposalKey(proposalKey).isPresent()) {
                continue; // identical proposal already imported (retry / duplicate file)
            }

            Candidate candidate = new Candidate();
            candidate.setBatchId(batch.getId());
            candidate.setSourceLang(sourceLang);
            candidate.setTargetLang(targetLang);
            candidate.setProductLine(batch.getProductLine());
            candidate.setVendor(batch.getVendor());
            candidate.setSourceText(tu.source());
            candidate.setProposedTarget(tu.target());
            candidate.setIdentityKey(identityKey);
            candidate.setGroupKey(groupKey);
            candidate.setProposalKey(proposalKey);

            List<ValidationService.Anomaly> anomalies = validationService.validate(
                    tu.source(), tu.target(), targetLang, mappingRulesOf(batch));
            if (!anomalies.isEmpty()) {
                candidate.setStatus(CandidateStatus.BLOCKED);
            }

            // Conflict detection: any prior, different proposal for the same identity in this scope
            // opens (or re-opens) a traceable conflict group. Decided candidates are never flipped.
            List<Candidate> siblings = candidateRepository.findByGroupKeyOrderById(groupKey);
            boolean differsFromSibling = siblings.stream()
                    .anyMatch(s -> !s.getProposalKey().equals(proposalKey));
            if (differsFromSibling && anomalies.isEmpty()) {
                ConflictGroup group = conflictGroupRepository.findByIdentityKey(groupKey)
                        .orElseGet(() -> {
                            ConflictGroup g = new ConflictGroup();
                            g.setIdentityKey(groupKey);
                            return conflictGroupRepository.saveAndFlush(g);
                        });
                if (group.getStatus() == ConflictGroup.Status.RESOLVED) {
                    group.setStatus(ConflictGroup.Status.OPEN); // new round; old resolution stays on record
                    conflictGroupRepository.save(group);
                }
                candidate.setConflictGroupId(group.getId());
                candidate.setStatus(CandidateStatus.CONFLICT);
                attachUndecidedSiblings(siblings, group);
            }

            Candidate saved;
            try {
                saved = candidateRepository.saveAndFlush(candidate);
            } catch (DataIntegrityViolationException race) {
                continue; // concurrent replay inserted the same proposal first
            }
            for (ValidationService.Anomaly anomaly : anomalies) {
                CandidateAnomaly row = new CandidateAnomaly();
                row.setCandidateId(saved.getId());
                row.setType(anomaly.type());
                row.setDetail(anomaly.detailJson());
                anomalyRepository.save(row);
            }
        }
    }

    private void attachUndecidedSiblings(List<Candidate> siblings, ConflictGroup group) {
        for (Candidate sibling : siblings) {
            // Decided candidates keep their decision; only undecided ones enter the conflict.
            if (sibling.getStatus() == CandidateStatus.PENDING) {
                sibling.setStatus(CandidateStatus.CONFLICT);
                sibling.setConflictGroupId(group.getId());
                candidateRepository.save(sibling);
            } else if (sibling.getStatus() == CandidateStatus.CONFLICT
                    && sibling.getConflictGroupId() == null) {
                sibling.setConflictGroupId(group.getId());
                candidateRepository.save(sibling);
            }
        }
    }

    private String mappingRulesOf(Batch batch) {
        return batch.getMappingRulesSnapshot();
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidatesOf(long batchId) {
        return candidateRepository.findByBatchIdOrderById(batchId);
    }
}
