package com.tmhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.tmhub.domain.*;
import com.tmhub.repo.*;
import com.tmhub.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scenario 1: incremental packages for two languages and multiple product lines import cleanly;
 * reviewers interleave accepts/rejects; the task is paused and resumed; only accepted entries migrate.
 */
class IncrementalImportAcceptanceTest extends BaseAcceptanceTest {

    @Autowired PublishService publishService;
    @Autowired ReviewService reviewService;
    @Autowired MigrationEngine migrationEngine;
    @Autowired CandidateRepository candidateRepository;
    @Autowired MigrationCommitRepository commitRepository;
    @Autowired TmEntryRepository entryRepository;
    @Autowired VersionBatchRepository versionBatchRepository;

    @Test
    void twoLanguagesTwoProductLines_pauseResume_onlyAcceptedMigrated() {
        TmVersion baseline = publishService.createBaseline("v0", "tester");

        long batchDe = importBatch(baseline.getId(), "en", "de", "mail", "vendor-a",
                tmx("en", "de", new String[][]{
                        {"Open {0}", "Öffnen {0}"},
                        {"Save file", "Datei speichern"},
                        {"Delete file", "Datei löschen"}}), null);
        long batchFr = importBatch(baseline.getId(), "en", "fr", "mail", "vendor-b",
                tmx("en", "fr", new String[][]{
                        {"Open {0}", "Ouvrir {0}"},
                        {"Close window", "Fermer la fenêtre"}}), null);
        long batchCalendar = importBatch(baseline.getId(), "en", "de", "calendar", "vendor-a",
                tmx("en", "de", new String[][]{
                        {"Next week", "Nächste Woche"},
                        {"Previous week", "Vorherige Woche"}}), null);

        assertThat(batchService.candidatesOf(batchDe)).hasSize(3);
        assertThat(batchService.candidatesOf(batchFr)).hasSize(2);
        assertThat(batchService.candidatesOf(batchCalendar)).hasSize(2);

        // Interleaved accept / reject across batches and reviewers.
        List<Candidate> deCandidates = batchService.candidatesOf(batchDe);
        List<Candidate> frCandidates = batchService.candidatesOf(batchFr);
        List<Candidate> calCandidates = batchService.candidatesOf(batchCalendar);

        reviewService.decide(deCandidates.get(0).getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, "ok", null));
        reviewService.decide(deCandidates.get(1).getId(),
                new ReviewService.DecisionCommand("bob", DecisionAction.REJECT, null, "wrong glossary", null));
        reviewService.decide(frCandidates.get(0).getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        reviewService.decide(deCandidates.get(2).getId(),
                new ReviewService.DecisionCommand("carol", DecisionAction.SET_FINAL,
                        "Datei endgültig löschen", "vendor wording too strong", null));
        reviewService.decide(frCandidates.get(1).getId(),
                new ReviewService.DecisionCommand("bob", DecisionAction.REJECT, null, "out of scope", null));
        reviewService.decide(calCandidates.get(0).getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        reviewService.decide(calCandidates.get(1).getId(),
                new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));

        // Decisions carry operator, time, rationale and previous state.
        var decisions = reviewService.historyOf(deCandidates.get(2).getId());
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getReviewer()).isEqualTo("carol");
        assertThat(decisions.get(0).getRationale()).isEqualTo("vendor wording too strong");
        assertThat(decisions.get(0).getPreviousStatus()).isEqualTo("PENDING");
        assertThat(decisions.get(0).getDecidedAt()).isNotNull();

        // Task over the German mail batch: pause after the first chunk, then resume.
        MigrationTask task = migrationEngine.createTask(batchDe, "de-mail");
        migrationEngine.start(task.getId());
        MigrationTask afterFirstChunk = migrationEngine.processChunk(task.getId()); // chunk size = 2
        migrationEngine.pause(task.getId());

        MigrationTask paused = migrationEngine.get(task.getId());
        assertThat(paused.getStatus()).isEqualTo(TaskStatus.PAUSED);
        assertThat(paused.getLastCandidateId()).isGreaterThan(0);
        long commitsAtPause = commitRepository.findByTaskIdOrderById(task.getId()).size();
        assertThat(commitsAtPause).isEqualTo(1); // first chunk holds candidates 1+2, only #1 accepted

        // Paused engine must not commit further.
        MigrationTask stillPaused = migrationEngine.processChunk(task.getId());
        assertThat(stillPaused.getStatus()).isEqualTo(TaskStatus.PAUSED);
        assertThat(commitRepository.findByTaskIdOrderById(task.getId())).hasSize(1);

        migrationEngine.resume(task.getId());
        MigrationTask done = migrationEngine.get(task.getId());
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETED);

        // Exactly the two accepted candidates of the batch were committed — never the rejected one.
        var commits = commitRepository.findByTaskIdOrderById(task.getId());
        assertThat(commits).hasSize(2);
        assertThat(commits.stream().map(MigrationCommit::getCandidateId))
                .containsExactlyInAnyOrder(deCandidates.get(0).getId(), deCandidates.get(2).getId());
        assertThat(candidateRepository.findById(deCandidates.get(1).getId()).orElseThrow().isCommitted())
                .isFalse();

        // Migrate the other two batches to completion as well.
        for (long batchId : new long[]{batchFr, batchCalendar}) {
            MigrationTask t = migrationEngine.createTask(batchId, "batch-" + batchId);
            migrationEngine.start(t.getId());
            migrationEngine.run(t.getId());
            assertThat(migrationEngine.get(t.getId()).getStatus()).isEqualTo(TaskStatus.COMPLETED);
        }

        // Publish: new version with parent, batch manifest and checksum; only accepted entries in it.
        TmVersion release = publishService.publish("v1", List.of(batchDe, batchFr, batchCalendar), "pm");
        assertThat(release.getParentId()).isEqualTo(baseline.getId());
        assertThat(release.getChecksum()).hasSize(64);
        assertThat(versionBatchRepository.findByVersionId(release.getId()))
                .extracting(VersionBatch::getBatchId)
                .containsExactlyInAnyOrder(batchDe, batchFr, batchCalendar);

        List<TmEntry> entries = entryRepository.findByVersionIdOrderById(release.getId());
        assertThat(entries).hasSize(5); // 2 accepted de-mail + 1 accepted fr + 2 accepted calendar
        assertThat(entries.stream().map(TmEntry::getTargetText))
                .contains("Öffnen {0}", "Datei endgültig löschen", "Ouvrir {0}",
                        "Nächste Woche", "Vorherige Woche")
                .doesNotContain("Datei speichern", "Fermer la fenêtre");
        // The SET_FINAL wording wins over the vendor proposal.
        assertThat(entries.stream().filter(e -> e.getOriginCandidateId() != null
                        && e.getOriginCandidateId().equals(deCandidates.get(2).getId()))
                .findFirst().orElseThrow().getTargetText())
                .isEqualTo("Datei endgültig löschen");
    }
}
