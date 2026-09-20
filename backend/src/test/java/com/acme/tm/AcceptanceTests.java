package com.acme.tm;

import com.acme.tm.error.ApiException;
import com.acme.tm.error.BlockedPublishException;
import com.acme.tm.model.*;
import com.acme.tm.repo.*;
import com.acme.tm.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance scenarios:
 *  1. normal import (two languages, multiple product lines), interleaved review,
 *     pause/resume of the migration task, only accepted entries migrate;
 *  2. duplicate upload, out-of-order shards, concurrent reviewers — single batch,
 *     single review result, single migration commit, conflicts fully recorded;
 *  3. missing placeholder / case violation / polysemous term — quarantined until
 *     manual resolution, valid entries never dropped;
 *  4. service restart during migration/export — checkpoint resume, no duplicate
 *     commits, stable checksum, traceable task state;
 *  5. regression of the legacy single-vendor import, old-version download and
 *     plain term replacement.
 */
@SpringBootTest
@ActiveProfiles("test")
class AcceptanceTests {

    @Autowired BatchService batchService;
    @Autowired ReviewService reviewService;
    @Autowired MigrationTaskService taskService;
    @Autowired PublishService publishService;
    @Autowired LegacyService legacyService;

    @Autowired LanguageProfileRepo profileRepo;
    @Autowired TermMappingRuleRepo ruleRepo;
    @Autowired TmVersionRepo versionRepo;
    @Autowired TmEntryRepo entryRepo;
    @Autowired ImportBatchRepo batchRepo;
    @Autowired BatchCandidateRepo candidateRepo;
    @Autowired ReviewDecisionRepo decisionRepo;
    @Autowired ConflictGroupRepo conflictRepo;
    @Autowired MigrationTaskRepo taskRepo;
    @Autowired MigrationCommitRepo commitRepo;
    @Autowired TaskEventRepo eventRepo;
    @Autowired UploadSessionRepo sessionRepo;
    @Autowired UploadChunkRepo chunkRepo;

    /** q: srcLang, src, tgtLang, tgt ... */
    private static String tmx2(String... q) {
        StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><tmx version=\"1.4\"><body>");
        for (int i = 0; i < q.length; i += 4) {
            sb.append("<tu><tuv xml:lang=\"").append(q[i]).append("\"><seg>").append(q[i + 1])
              .append("</seg></tuv><tuv xml:lang=\"").append(q[i + 2]).append("\"><seg>").append(q[i + 3])
              .append("</seg></tuv></tu>");
        }
        return sb.append("</body></tmx>").toString();
    }

    private String deCleanTmx;   // 4 valid en->de entries, product line alpha
    private String jaCleanTmx;   // 2 valid en->ja entries, product line beta
    private String deAnomalyTmx; // placeholder/case/polysemy anomalies

    @BeforeEach
    void setUp() {
        chunkRepo.deleteAll(); sessionRepo.deleteAll();
        eventRepo.deleteAll(); commitRepo.deleteAll(); taskRepo.deleteAll();
        decisionRepo.deleteAll(); candidateRepo.deleteAll(); conflictRepo.deleteAll(); batchRepo.deleteAll();
        entryRepo.deleteAll(); versionRepo.deleteAll();
        ruleRepo.deleteAll(); profileRepo.deleteAll();

        profileRepo.save(new LanguageProfile("en", CaseRule.PRESERVE));
        profileRepo.save(new LanguageProfile("de", CaseRule.LOWER));
        profileRepo.save(new LanguageProfile("ja", CaseRule.PRESERVE));
        ruleRepo.save(new TermMappingRule("en", "de", "alpha", "server", "server", "host", false, null, null));
        ruleRepo.save(new TermMappingRule("en", "de", "alpha", "bank", "Bank", null, true, "Bank;Ufer", "financial vs. river"));
        ruleRepo.save(new TermMappingRule("en", "ja", "beta", "server", "サーバー", null, false, null, null));

        deCleanTmx = tmx2(
                "en", "Open {0} file", "de", "Datei {0} öffnen",
                "en", "Save {0} now", "de", "{0} jetzt speichern",
                "en", "Close window", "de", "Fenster schließen",
                "en", "Print {0} page", "de", "Seite {0} drucken");
        jaCleanTmx = tmx2(
                "en", "Open {0} file", "ja", "{0} を開く",
                "en", "The server is down", "ja", "サーバーがダウンしています");
        deAnomalyTmx = tmx2(
                "en", "Open {0} file", "de", "Datei {0} öffnen",                                  // valid
                "en", "The server is down", "de", "Der Server ist ausgefallen",                    // CASE_VIOLATION
                "en", "The bank is closed", "de", "Die Bank ist geschlossen",                      // POLYSEMOUS_TERM
                "en", "Print {0} page", "de", "Seite drucken",                                     // PLACEHOLDER_MISMATCH
                "en", "Close window", "de", "Fenster schließen");                                  // valid
    }

    private List<BatchCandidate> candidatesOf(ImportBatch b) {
        return candidateRepo.findByBatchIdOrderByIdAsc(b.getId());
    }

    // ---------------------------------------------------------------- scenario 1

    @Test
    void scenario1_normalImportInterleavedReviewPauseResume() {
        TmVersion base = legacyService.importLegacy("V0", "en", "de", "alpha", "v1-baseline",
                tmx2("en", "Hello", "de", "Hallo"));

        // two languages, multiple product lines
        ImportBatch deBatch = batchService.importTmx("V1", "en", "de", "alpha", base.getId(), deCleanTmx);
        ImportBatch jaBatch = batchService.importTmx("V1", "en", "ja", "beta", base.getId(), jaCleanTmx);
        assertEquals(4, candidatesOf(deBatch).size());
        assertEquals(2, candidatesOf(jaBatch).size());
        assertEquals(base.getId(), deBatch.getSourceVersionId());
        assertNotNull(deBatch.getTmxFingerprint());
        assertTrue(deBatch.getMappingRules().contains("server"));

        // interleaved accept / reject
        List<BatchCandidate> de = candidatesOf(deBatch);
        reviewService.decide(de.get(0).getId(), "alice", ReviewAction.ACCEPT, null, null, "ok", 0);
        reviewService.decide(de.get(1).getId(), "alice", ReviewAction.REJECT, null, null, "bad style", 0);
        reviewService.decide(de.get(2).getId(), "bob", ReviewAction.ACCEPT, null, null, "ok", 0);
        reviewService.decide(de.get(3).getId(), "bob", ReviewAction.REJECT, null, null, "dup", 0);
        for (BatchCandidate c : candidatesOf(jaBatch)) {
            reviewService.decide(c.getId(), "alice", ReviewAction.ACCEPT, null, null, "ok", 0);
        }

        // pause / resume of the migration task
        MigrationTask task = taskService.create(deBatch.getId());
        taskService.run(task.getId(), 1, 1, null);                       // one chunk, then stop
        MigrationTask mid = taskService.mustLoad(task.getId());
        assertEquals(TaskStatus.RUNNING, mid.getStatus());
        assertEquals(1, mid.getCommittedCount());

        taskService.pause(task.getId());
        taskService.run(task.getId(), 1, 10, null);                      // no-op while paused
        assertEquals(1, taskService.mustLoad(task.getId()).getCommittedCount());

        taskService.resume(task.getId());
        MigrationTask done = taskService.run(task.getId(), 1, 10, null);
        assertEquals(TaskStatus.COMPLETED, done.getStatus());
        assertEquals(2, done.getCommittedCount());                       // only the two ACCEPTED

        // ja batch migrates too
        MigrationTask jaTask = taskService.create(jaBatch.getId());
        assertEquals(2, taskService.run(jaTask.getId(), 10, 10, null).getCommittedCount());

        // publish: only accepted entries land in the new version
        TmVersion v2 = publishService.publish("v2-incremental", List.of(deBatch.getId(), jaBatch.getId()));
        List<TmEntry> entries = entryRepo.findByVersionIdOrderByIdAsc(v2.getId());
        assertEquals(v2.getParentId(), base.getId());
        assertTrue(entries.stream().anyMatch(e -> e.getSourceText().equals("Hello")));            // inherited
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("Datei {0} öffnen"))); // accepted
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("Fenster schließen")));// accepted
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("{0} を開く")));           // accepted (ja)
        assertFalse(entries.stream().anyMatch(e -> e.getTargetText().equals("{0} jetzt speichern"))); // rejected
        assertFalse(entries.stream().anyMatch(e -> e.getTargetText().equals("Seite {0} drucken")));   // rejected
        assertEquals(5, entries.size());
        assertTrue(v2.isEffective());
        assertFalse(versionRepo.findById(base.getId()).orElseThrow().isEffective());
    }

    // ---------------------------------------------------------------- scenario 2

    @Test
    void scenario2_duplicateUploadOutOfOrderShardsConcurrentReview() throws Exception {
        // --- out-of-order shards + duplicate shard + duplicate completion
        String fp = Checksums.sha256(deCleanTmx);
        int third = deCleanTmx.length() / 3;
        String c0 = deCleanTmx.substring(0, third);
        String c1 = deCleanTmx.substring(third, 2 * third);
        String c2 = deCleanTmx.substring(2 * third);

        batchService.initSession("up-1", "V1", "en", "de", "alpha", 3, fp);
        batchService.addChunk("up-1", 2, c2);   // out of order
        batchService.addChunk("up-1", 0, c0);
        batchService.addChunk("up-1", 1, c1);
        batchService.addChunk("up-1", 1, c1);   // duplicate shard absorbed
        ImportBatch b1 = batchService.completeSession("up-1", null);
        ImportBatch b1again = batchService.completeSession("up-1", null);  // duplicate completion
        assertEquals(b1.getId(), b1again.getId());

        // --- same file uploaded again (retry / different path) -> same batch, no dup candidates
        ImportBatch b1retry = batchService.importTmx("V1", "en", "de", "alpha", null, deCleanTmx);
        assertEquals(b1.getId(), b1retry.getId());
        assertEquals(1, batchRepo.count());
        assertEquals(4, candidateRepo.count());

        // --- second vendor, same source sentence, diverging target -> traceable conflict
        String v2tmx = tmx2("en", "Close window", "de", "Fenster zumachen");
        ImportBatch b2 = batchService.importTmx("V2", "en", "de", "alpha", null, v2tmx);
        List<BatchCandidate> b2cands = candidatesOf(b2);
        assertEquals(CandidateStatus.CONFLICT, b2cands.get(0).getStatus());
        Long groupId = b2cands.get(0).getConflictGroupId();
        assertNotNull(groupId);
        List<BatchCandidate> chain = batchService.conflictChain(groupId);
        assertEquals(2, chain.size());           // V1's "Fenster schließen" + V2's "Fenster zumachen"
        assertTrue(chain.stream().allMatch(c -> c.getStatus() == CandidateStatus.CONFLICT));

        // --- two reviewers on the same candidate: sequential stale attempt -> 409
        BatchCandidate contested = chain.get(0);
        reviewService.decide(contested.getId(), "alice", ReviewAction.ACCEPT, null, null, "keep V1",
                contested.getVersion());
        ApiException stale = assertThrows(ApiException.class, () ->
                reviewService.decide(contested.getId(), "bob", ReviewAction.REJECT, null, null, "prefer V2",
                        contested.getVersion()));
        assertEquals(409, stale.getStatus().value());
        assertEquals(1, decisionRepo.count());   // exactly one review result

        // --- truly concurrent reviewers -> exactly one wins
        BatchCandidate other = chain.get(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        for (String reviewer : new String[]{"carol", "dave"}) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    reviewService.decide(other.getId(), reviewer, ReviewAction.ACCEPT, null, null, "race",
                            other.getVersion());
                    successes.incrementAndGet();
                } catch (ApiException e) {
                    if (e.getStatus().value() == 409) conflicts.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        assertEquals(1, successes.get(), "exactly one reviewer may win");
        assertEquals(1, conflicts.get(), "the loser must see a 409 version conflict");
        assertEquals(2, decisionRepo.count());

        // --- conflict chain and decisions fully recorded
        List<ReviewDecision> history = reviewService.history(
                List.of(contested.getId(), other.getId()));
        assertEquals(2, history.size());
        assertTrue(history.stream().allMatch(d -> d.getReviewer() != null && d.getCreatedAt() != null));
        assertEquals("RESOLVED", conflictRepo.findById(groupId).orElseThrow().getStatus());

        // --- migration retried: commits stay unique
        List<BatchCandidate> all = candidatesOf(b1);
        for (BatchCandidate c : all) {
            if (c.getStatus() == CandidateStatus.PENDING) {
                reviewService.decide(c.getId(), "alice", ReviewAction.ACCEPT, null, null, "ok", c.getVersion());
            }
        }
        long accepted = candidateRepo.findByBatchIdAndStatus(b1.getId(), CandidateStatus.ACCEPTED).size();
        MigrationTask t1 = taskService.create(b1.getId());
        taskService.run(t1.getId(), 2, 20, null);
        MigrationTask t2 = taskService.create(b1.getId());       // retry with a second task
        taskService.run(t2.getId(), 2, 20, null);
        assertEquals(accepted, commitRepo.count());              // no double commits
        assertEquals(accepted, candidateRepo.findByBatchIdAndStatus(b1.getId(), CandidateStatus.MIGRATED).size());
    }

    // ---------------------------------------------------------------- scenario 3

    @Test
    void scenario3_anomaliesQuarantinedUntilManualResolution() {
        ImportBatch batch = batchService.importTmx("V1", "en", "de", "alpha", null, deAnomalyTmx);
        List<BatchCandidate> cs = candidatesOf(batch);
        assertEquals(5, cs.size());

        BatchCandidate valid1 = cs.get(0);
        BatchCandidate caseBad = cs.get(1);
        BatchCandidate poly = cs.get(2);
        BatchCandidate phBad = cs.get(3);
        BatchCandidate valid2 = cs.get(4);

        assertEquals(CandidateStatus.PENDING, valid1.getStatus());
        assertEquals(CandidateStatus.PENDING, valid2.getStatus());
        assertEquals(CandidateStatus.BLOCKED, caseBad.getStatus());
        assertEquals(AnomalyType.CASE_VIOLATION, caseBad.getAnomalyType());
        assertEquals(CandidateStatus.BLOCKED, poly.getStatus());
        assertEquals(AnomalyType.POLYSEMOUS_TERM, poly.getAnomalyType());
        assertEquals(CandidateStatus.BLOCKED, phBad.getStatus());
        assertEquals(AnomalyType.PLACEHOLDER_MISMATCH, phBad.getAnomalyType());
        assertTrue(phBad.getAnomalyDetail().contains("{0}"));

        // anomalies refuse to auto-resolve: no guessing
        assertThrows(ApiException.class, () -> reviewService.decide(
                phBad.getId(), "alice", ReviewAction.ACCEPT, null, null, "ignore", 0));
        assertThrows(ApiException.class, () -> reviewService.decide(
                poly.getId(), "alice", ReviewAction.ACCEPT, null, null, "ignore", 0));
        assertThrows(ApiException.class, () -> reviewService.decide(
                caseBad.getId(), "alice", ReviewAction.ACCEPT, null, null, "ignore", 0));
        // unapproved polysemy choice rejected too
        assertThrows(ApiException.class, () -> reviewService.decide(
                poly.getId(), "alice", ReviewAction.ACCEPT, "Fluss", null, "not approved", 0));

        // publish is blocked while quarantined entries exist
        reviewService.decide(valid1.getId(), "alice", ReviewAction.ACCEPT, null, null, "ok", 0);
        reviewService.decide(valid2.getId(), "bob", ReviewAction.ACCEPT, null, null, "ok", 0);
        MigrationTask t = taskService.create(batch.getId());
        taskService.run(t.getId(), 10, 10, null);
        BlockedPublishException blocked = assertThrows(BlockedPublishException.class, () ->
                publishService.publish("v2", List.of(batch.getId())));
        assertEquals(3, blocked.getBlockers().size());

        // manual resolutions
        reviewService.decide(phBad.getId(), "alice", ReviewAction.RESOLVE, null,
                "Seite {0} drucken", "placeholder restored", 0);
        BatchCandidate polyResolved = reviewService.decide(poly.getId(), "bob", ReviewAction.RESOLVE,
                "Ufer", null, "river bank per context", 0);
        assertTrue(polyResolved.getTargetText().contains("Ufer"));
        reviewService.decide(caseBad.getId(), "alice", ReviewAction.RESOLVE, null,
                "Der server ist ausgefallen", "lowercase per de profile", 0);

        // migrate the newly accepted, then publish succeeds; valid entries were never dropped
        MigrationTask t2 = taskService.create(batch.getId());
        taskService.run(t2.getId(), 10, 10, null);
        TmVersion v = publishService.publish("v2", List.of(batch.getId()));
        List<TmEntry> entries = entryRepo.findByVersionIdOrderByIdAsc(v.getId());
        assertEquals(5, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("Datei {0} öffnen")));
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("Fenster schließen")));
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("Seite {0} drucken")));
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("Die Ufer ist geschlossen")));
        assertTrue(entries.stream().anyMatch(e -> e.getTargetText().equals("Der server ist ausgefallen")));
    }

    // ---------------------------------------------------------------- scenario 4

    @Test
    void scenario4_restartResumeCheckpointStableChecksum() {
        ImportBatch batch = batchService.importTmx("V1", "en", "de", "alpha", null, deCleanTmx);
        for (BatchCandidate c : candidatesOf(batch)) {
            reviewService.decide(c.getId(), "alice", ReviewAction.ACCEPT, null, null, "ok", 0);
        }

        // crash after 2 commits == service restart mid-migration
        MigrationTask task = taskService.create(batch.getId());
        MigrationTask crashed = taskService.run(task.getId(), 1, 100, 2);
        assertEquals(TaskStatus.FAILED, crashed.getStatus());
        assertEquals(2, crashed.getCommittedCount());
        long checkpoint = crashed.getCheckpointCandidateId();
        assertTrue(checkpoint > 0);
        assertNotNull(crashed.getError());

        // resume from checkpoint: reviewed entries are not committed twice
        taskService.resume(task.getId());
        MigrationTask done = taskService.run(task.getId(), 1, 100, null);
        assertEquals(TaskStatus.COMPLETED, done.getStatus());
        assertEquals(4, done.getCommittedCount());
        assertEquals(4, commitRepo.count());
        assertEquals(4, commitRepo.findAll().stream().map(MigrationCommit::getCandidateId).distinct().count());

        // task state fully traceable
        List<String> transitions = eventRepo.findByTaskIdOrderByIdAsc(task.getId()).stream()
                .map(TaskEvent::getToStatus).toList();
        assertTrue(transitions.containsAll(List.of("PENDING", "RUNNING", "FAILED", "COMPLETED")));

        // export is deterministic: identical checksum across repeated exports (restart-safe)
        TmVersion v = publishService.publish("v2", List.of(batch.getId()));
        String tmx1 = publishService.exportTmx(v.getId());
        String tmx2 = publishService.exportTmx(v.getId());
        assertEquals(tmx1, tmx2);
        assertEquals(Checksums.sha256(tmx1), Checksums.sha256(tmx2));
        assertEquals(v.getChecksum(), publishService.checksumOf(entryRepo.findByVersionId(v.getId())));
    }

    // ---------------------------------------------------------------- scenario 5

    @Test
    void scenario5_legacyFlowsUnaffectedByBatchMechanism() {
        // legacy single-vendor import still works and becomes effective
        TmVersion v1 = legacyService.importLegacy("V0", "en", "de", "alpha", "v1-legacy",
                tmx2("en", "Hello", "de", "Hallo",
                     "en", "The host is down", "de", "Der host ist ausgefallen"));
        assertTrue(v1.isEffective());

        // legacy plain term replacement (host -> server, de lowercase rule)
        String replaced = legacyService.replaceTerms("Der host ist ausgefallen", "en", "de", "alpha");
        assertEquals("Der server ist ausgefallen", replaced);
        // polysemous terms refuse to guess
        assertThrows(ApiException.class, () ->
                legacyService.replaceTerms("Die bank heute", "en", "de", "alpha"));

        // new batch mechanism does not disturb the effective legacy version
        ImportBatch batch = batchService.importTmx("V1", "en", "de", "alpha", v1.getId(), deCleanTmx);
        assertTrue(versionRepo.findById(v1.getId()).orElseThrow().isEffective());
        for (BatchCandidate c : candidatesOf(batch)) {
            reviewService.decide(c.getId(), "alice", ReviewAction.ACCEPT, null, null, "ok", 0);
        }
        MigrationTask t = taskService.create(batch.getId());
        taskService.run(t.getId(), 10, 10, null);
        TmVersion v2 = publishService.publish("v2-batches", List.of(batch.getId()));
        assertTrue(v2.isEffective());

        // old version download stays available
        String oldTmx = publishService.exportTmx(v1.getId());
        assertTrue(oldTmx.contains("Hallo"));
        assertEquals("/api/versions/" + v1.getId() + "/tmx",
                versionRepo.findById(v1.getId()).orElseThrow().getDownloadUrl());

        // rollback creates a reverse version with a reason; history is never rewritten
        TmVersion rb = publishService.rollback(v1.getId(), "regression in v2-batches");
        assertEquals(VersionStatus.ROLLBACK, rb.getStatus());
        assertEquals("regression in v2-batches", rb.getRollbackReason());
        assertEquals(v2.getId(), rb.getParentId());
        assertTrue(rb.isEffective());
        assertEquals(versionRepo.findById(v1.getId()).orElseThrow().getChecksum(), rb.getChecksum());

        // lineage: rollback -> v2 -> v1, all versions still present
        List<TmVersion> lineage = publishService.lineage(rb.getId());
        assertEquals(3, lineage.size());
        assertEquals(3, versionRepo.count());
        assertEquals(VersionStatus.SUPERSEDED, versionRepo.findById(v1.getId()).orElseThrow().getStatus());
    }
}
