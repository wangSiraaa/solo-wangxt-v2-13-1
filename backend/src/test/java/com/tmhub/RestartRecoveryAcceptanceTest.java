package com.tmhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.tmhub.domain.*;
import com.tmhub.repo.*;
import com.tmhub.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Scenario 4: a service restart during migration or TMX export resumes from the checkpoint —
 * no double commits, stable checksums, traceable task state.
 */
class RestartRecoveryAcceptanceTest extends BaseAcceptanceTest {

    @Autowired PublishService publishService;
    @Autowired ReviewService reviewService;
    @Autowired MigrationEngine migrationEngine;
    @Autowired ExportService exportService;
    @Autowired MigrationCommitRepository commitRepository;
    @Autowired TmEntryRepository entryRepository;
    @Autowired ExportTaskRepository exportTaskRepository;

    @Test
    void migrationRestart_resumesFromCheckpoint_withoutDuplicateCommits() {
        TmVersion baseline = publishService.createBaseline("v0", "tester");
        long batchId = importBatch(baseline.getId(), "en", "de", "mail", "vendor-a",
                tmx("en", "de", new String[][]{
                        {"S1", "T1"}, {"S2", "T2"}, {"S3", "T3"}, {"S4", "T4"}, {"S5", "T5"}}), null);
        for (Candidate c : batchService.candidatesOf(batchId)) {
            reviewService.decide(c.getId(),
                    new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        }

        MigrationTask task = migrationEngine.createTask(batchId, "restartable");
        migrationEngine.start(task.getId());

        // Chunk size is 2: first chunk commits 2 of 5, then the "process dies".
        migrationEngine.processChunk(task.getId());
        MigrationTask beforeCrash = migrationEngine.get(task.getId());
        assertThat(beforeCrash.getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(beforeCrash.getCommittedCount()).isEqualTo(2);
        long checkpointAtCrash = beforeCrash.getLastCandidateId();

        // Restart recovery: RUNNING -> INTERRUPTED, checkpoint preserved.
        assertThat(migrationEngine.recoverInterruptedTasks()).isEqualTo(1);
        MigrationTask interrupted = migrationEngine.get(task.getId());
        assertThat(interrupted.getStatus()).isEqualTo(TaskStatus.INTERRUPTED);
        assertThat(interrupted.getLastCandidateId()).isEqualTo(checkpointAtCrash);
        assertThat(interrupted.getError()).contains("checkpoint");

        // Resume: only the remaining 3 candidates are committed; total stays 5, no duplicates.
        migrationEngine.resume(task.getId());
        MigrationTask done = migrationEngine.get(task.getId());
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(done.getCommittedCount()).isEqualTo(5);

        var commits = commitRepository.findByTaskIdOrderById(task.getId());
        assertThat(commits).hasSize(5);
        assertThat(commits.stream().map(MigrationCommit::getCandidateId).distinct()).hasSize(5);

        // A second resume is a no-op — nothing left, nothing duplicated.
        migrationEngine.resume(task.getId());
        assertThat(commitRepository.findByTaskIdOrderById(task.getId())).hasSize(5);
    }

    @Test
    void failedTask_resumesFromCheckpoint() {
        TmVersion baseline = publishService.createBaseline("v0", "tester");
        long batchId = importBatch(baseline.getId(), "en", "de", "mail", "vendor-a",
                tmx("en", "de", new String[][]{{"X1", "Y1"}, {"X2", "Y2"}, {"X3", "Y3"}}), null);
        for (Candidate c : batchService.candidatesOf(batchId)) {
            reviewService.decide(c.getId(),
                    new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        }
        MigrationTask task = migrationEngine.createTask(batchId, "failable");
        migrationEngine.start(task.getId());
        migrationEngine.processChunk(task.getId()); // commits 2 of 3

        // Something kills the chunk mid-flight: the engine records FAILED with the checkpoint.
        migrationEngine.markFailed(task.getId(), "simulated infrastructure failure");
        MigrationTask failed = migrationEngine.get(task.getId());
        assertThat(failed.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.getCommittedCount()).isEqualTo(2);

        migrationEngine.resume(task.getId());
        MigrationTask done = migrationEngine.get(task.getId());
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(done.getCommittedCount()).isEqualTo(3);
        assertThat(commitRepository.findByTaskIdOrderById(task.getId())).hasSize(3);
    }

    @Test
    void exportRestart_resumesFromCheckpoint_checksumStable() {
        TmVersion baseline = publishService.createBaseline("v0", "tester");
        long batchId = importBatch(baseline.getId(), "en", "fr", "mail", "vendor-a",
                tmx("en", "fr", new String[][]{
                        {"A1", "B1"}, {"A2", "B2"}, {"A3", "B3"}, {"A4", "B4"},
                        {"A5", "B5"}, {"A6", "B6"}, {"A7", "B7"}}), null);
        for (Candidate c : batchService.candidatesOf(batchId)) {
            reviewService.decide(c.getId(),
                    new ReviewService.DecisionCommand("alice", DecisionAction.ACCEPT, null, null, null));
        }
        MigrationTask task = migrationEngine.createTask(batchId, "m");
        migrationEngine.start(task.getId());
        migrationEngine.run(task.getId());
        TmVersion release = publishService.publish("rel", List.of(batchId), "pm");

        // Export with chunk size 3: process one chunk, then "restart".
        ExportTask export = exportService.startExport(release.getId());
        exportService.processChunk(export.getId());
        ExportTask midExport = exportService.getTask(export.getId());
        assertThat(midExport.getStatus()).isEqualTo(ExportStatus.RUNNING);
        assertThat(midExport.getExported()).isEqualTo(3);

        assertThat(exportService.recoverInterruptedExports()).isEqualTo(1);
        assertThat(exportService.getTask(export.getId()).getStatus()).isEqualTo(ExportStatus.INTERRUPTED);

        exportService.resume(export.getId());
        ExportTask finished = exportService.getTask(export.getId());
        assertThat(finished.getStatus()).isEqualTo(ExportStatus.COMPLETED);
        assertThat(finished.getExported()).isEqualTo(7);
        assertThat(finished.getDownloadUrl()).isEqualTo("/api/versions/" + release.getId() + "/export");

        // A second, uninterrupted export of the same version must produce the identical checksum.
        ExportTask reference = exportService.startExport(release.getId());
        exportService.run(reference.getId());
        assertThat(exportService.getTask(reference.getId()).getChecksum())
                .isEqualTo(finished.getChecksum());

        // Artifact content matches the version's content checksum inputs and stays downloadable.
        ExportArtifact artifact = exportService.latestArtifact(release.getId());
        assertThat(artifact.getChecksum()).isEqualTo(finished.getChecksum());
        assertThat(artifact.getContent()).contains("<tmx version=\"1.4\">", "A7", "B7");
        assertThat(publishService.computeChecksum(release.getId())).isEqualTo(release.getChecksum());
    }
}
