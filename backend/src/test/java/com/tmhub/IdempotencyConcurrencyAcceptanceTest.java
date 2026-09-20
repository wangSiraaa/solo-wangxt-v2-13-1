package com.tmhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.tmhub.domain.*;
import com.tmhub.repo.*;
import com.tmhub.service.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scenario 2: duplicate file uploads, out-of-order shards and two reviewers on the same candidate —
 * the batch, the review outcome and the migration commits each exist exactly once, with the
 * conflict fully recorded.
 */
class IdempotencyConcurrencyAcceptanceTest extends BaseAcceptanceTest {

    @Autowired PublishService publishService;
    @Autowired ReviewService reviewService;
    @Autowired MigrationEngine migrationEngine;
    @Autowired BatchRepository batchRepository;
    @Autowired CandidateRepository candidateRepository;
    @Autowired ConflictGroupRepository conflictGroupRepository;
    @Autowired ReviewDecisionRepository decisionRepository;
    @Autowired MigrationCommitRepository commitRepository;

    @Test
    void duplicateUploads_outOfOrderShards_concurrentReview_singleOutcome() throws Exception {
        TmVersion baseline = publishService.createBaseline("v0", "tester");
        String tmx = tmx("en", "de", new String[][]{
                {"Open {0}", "Öffnen {0}"},
                {"Save file", "Datei speichern"},
                {"Close window", "Fenster schließen"}});
        byte[] bytes = tmx.getBytes(StandardCharsets.UTF_8);
        String fingerprint = Hashes.sha256(bytes);

        // --- duplicate file upload: initiate twice with identical delivery metadata ---
        var cmd = new BatchService.InitiateCommand(baseline.getId(), "en", "de", "mail", "vendor-a",
                fingerprint, null, null, 3);
        Batch first = batchService.initiate(cmd);
        Batch second = batchService.initiate(cmd);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(batchRepository.count()).isEqualTo(1);

        // --- out-of-order shards: split into three parts, deliver 2, 0, 1 ---
        int third = bytes.length / 3;
        byte[][] shards = new byte[][]{
                java.util.Arrays.copyOfRange(bytes, 0, third),
                java.util.Arrays.copyOfRange(bytes, third, 2 * third),
                java.util.Arrays.copyOfRange(bytes, 2 * third, bytes.length)};
        Batch afterTwo = batchService.uploadShard(first.getId(), 2, shards[2]);
        assertThat(afterTwo.getStatus()).isEqualTo(BatchStatus.RECEIVING);
        assertThat(afterTwo.getReceivedShards()).isEqualTo(1);
        batchService.uploadShard(first.getId(), 0, shards[0]);
        Batch complete = batchService.uploadShard(first.getId(), 1, shards[1]);
        assertThat(complete.getStatus()).isEqualTo(BatchStatus.READY);

        // Re-uploading a shard and re-delivering the whole file changes nothing.
        batchService.uploadShard(first.getId(), 1, shards[1]);
        Batch replayed = batchService.initiate(cmd);
        batchService.uploadShard(replayed.getId(), 0, shards[0]);
        assertThat(batchRepository.count()).isEqualTo(1);
        List<Candidate> candidates = batchService.candidatesOf(first.getId());
        assertThat(candidates).hasSize(3);

        // --- a second vendor proposes a different target for the same identity: conflict ---
        String conflictTmx = tmx("en", "de", new String[][]{{"Save file", "Datei sichern"}});
        long vendorBBatch = importBatch(baseline.getId(), "en", "de", "mail", "vendor-b",
                conflictTmx, null);
        List<Candidate> vendorBCandidates = batchService.candidatesOf(vendorBBatch);
        assertThat(vendorBCandidates).hasSize(1);
        Candidate conflicting = vendorBCandidates.get(0);
        assertThat(conflicting.getStatus()).isEqualTo(CandidateStatus.CONFLICT);
        assertThat(conflicting.getConflictGroupId()).isNotNull();

        // The original, still-undecided proposal for the same identity joins the conflict chain.
        Candidate original = candidates.stream()
                .filter(c -> c.getProposedTarget().equals("Datei speichern")).findFirst().orElseThrow();
        assertThat(candidateRepository.findById(original.getId()).orElseThrow().getStatus())
                .isEqualTo(CandidateStatus.CONFLICT);
        ConflictGroup group = conflictGroupRepository.findById(conflicting.getConflictGroupId()).orElseThrow();
        assertThat(group.getStatus()).isEqualTo(ConflictGroup.Status.OPEN);
        assertThat(candidateRepository.findByConflictGroupIdOrderById(group.getId())).hasSize(2);

        // --- two reviewers decide the same candidate at the same time ---
        Candidate open = candidates.get(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Object> results = new CopyOnWriteArrayList<>();
        Runnable reviewer = () -> {
            try {
                ready.countDown();
                go.await();
                Candidate decided = reviewService.decide(open.getId(),
                        new ReviewService.DecisionCommand("reviewer", DecisionAction.ACCEPT, null, "race", null));
                results.add(decided);
            } catch (VersionConflictException e) {
                results.add(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread t1 = new Thread(reviewer);
        Thread t2 = new Thread(reviewer);
        t1.start();
        t2.start();
        ready.await();
        go.countDown();
        t1.join();
        t2.join();

        long successes = results.stream().filter(r -> r instanceof Candidate).count();
        long conflicts = results.stream().filter(r -> r instanceof VersionConflictException).count();
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);

        // Exactly one human decision exists for that candidate; its state is decided once.
        List<ReviewDecision> decisions = reviewService.historyOf(open.getId());
        assertThat(decisions).hasSize(1);
        assertThat(candidateRepository.findById(open.getId()).orElseThrow().getStatus())
                .isEqualTo(CandidateStatus.ACCEPTED);

        // --- resolve the conflict: accepting one side auto-supersedes the other, traceably ---
        reviewService.decide(original.getId(),
                new ReviewService.DecisionCommand("lead", DecisionAction.ACCEPT, null,
                        "glossary prefers 'speichern'", null));
        ConflictGroup resolved = conflictGroupRepository.findById(group.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(ConflictGroup.Status.RESOLVED);
        assertThat(resolved.getResolvedCandidateId()).isEqualTo(original.getId());
        assertThat(resolved.getResolvedBy()).isEqualTo("lead");
        assertThat(candidateRepository.findById(conflicting.getId()).orElseThrow().getStatus())
                .isEqualTo(CandidateStatus.REJECTED);
        List<ReviewDecision> loserHistory = reviewService.historyOf(conflicting.getId());
        assertThat(loserHistory).hasSize(1);
        assertThat(loserHistory.get(0).getAction()).isEqualTo(DecisionAction.AUTO_SUPERSEDE);
        assertThat(loserHistory.get(0).getRationale()).contains(String.valueOf(original.getId()));

        // --- migration commits each accepted candidate exactly once ---
        MigrationTask task = migrationEngine.createTask(first.getId(), "idempotent");
        migrationEngine.start(task.getId());
        migrationEngine.run(task.getId());
        var commits = commitRepository.findByTaskIdOrderById(task.getId());
        assertThat(commits.stream().map(MigrationCommit::getCandidateId).distinct())
                .hasSize(commits.size());
        assertThat(commits).hasSize(2); // open + original; the third vendor-a candidate is pending

        // Re-running the completed task cannot duplicate commits.
        migrationEngine.resume(task.getId());
        assertThat(commitRepository.findByTaskIdOrderById(task.getId())).hasSize(2);
    }
}
