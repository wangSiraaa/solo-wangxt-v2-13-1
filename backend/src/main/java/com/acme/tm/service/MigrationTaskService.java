package com.acme.tm.service;

import com.acme.tm.error.ApiException;
import com.acme.tm.model.*;
import com.acme.tm.repo.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Checkpoint-based migration engine. Each chunk commits in its own transaction and advances
 * the checkpoint cursor, so a pause, failure or service restart never loses or duplicates
 * work: resume picks up exactly after the last committed candidate. A candidate is committed
 * at most once across all tasks (unique constraint on migration_commit.candidate_id).
 */
@Service
public class MigrationTaskService {
    private final MigrationTaskRepo taskRepo;
    private final MigrationCommitRepo commitRepo;
    private final BatchCandidateRepo candidateRepo;
    private final ImportBatchRepo batchRepo;
    private final TaskEventRepo eventRepo;
    private final TransactionTemplate tx;

    /** Test hook: simulates a crash after N commits in the current run. */
    public static class SimulatedCrash extends RuntimeException {
        public SimulatedCrash(String msg) { super(msg); }
    }

    public MigrationTaskService(MigrationTaskRepo taskRepo, MigrationCommitRepo commitRepo,
                                BatchCandidateRepo candidateRepo, ImportBatchRepo batchRepo,
                                TaskEventRepo eventRepo,
                                org.springframework.transaction.PlatformTransactionManager tm) {
        this.taskRepo = taskRepo;
        this.commitRepo = commitRepo;
        this.candidateRepo = candidateRepo;
        this.batchRepo = batchRepo;
        this.eventRepo = eventRepo;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Transactional
    public MigrationTask create(Long batchId) {
        ImportBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> ApiException.notFound("batch " + batchId));
        MigrationTask task = taskRepo.save(new MigrationTask(batch.getId()));
        eventRepo.save(new TaskEvent(task.getId(), null, TaskStatus.PENDING.name(), "task created"));
        return task;
    }

    @Transactional
    public MigrationTask start(Long taskId) {
        MigrationTask t = mustLoad(taskId);
        transition(t, TaskStatus.RUNNING, "started");
        batchRepo.findById(t.getBatchId()).ifPresent(b -> {
            b.setStatus(BatchStatus.MIGRATING);
            batchRepo.save(b);
        });
        return t;
    }

    @Transactional
    public MigrationTask pause(Long taskId) {
        MigrationTask t = mustLoad(taskId);
        if (t.getStatus() != TaskStatus.RUNNING) {
            throw ApiException.conflict("task " + taskId + " is " + t.getStatus() + ", cannot pause");
        }
        return transition(t, TaskStatus.PAUSED, "paused at checkpoint candidate " + t.getCheckpointCandidateId());
    }

    @Transactional
    public MigrationTask resume(Long taskId) {
        MigrationTask t = mustLoad(taskId);
        if (t.getStatus() != TaskStatus.PAUSED && t.getStatus() != TaskStatus.FAILED) {
            throw ApiException.conflict("task " + taskId + " is " + t.getStatus() + ", cannot resume");
        }
        return transition(t, TaskStatus.RUNNING,
                "resumed from checkpoint candidate " + t.getCheckpointCandidateId());
    }

    /**
     * Processes up to {@code maxChunks} chunks of {@code chunkSize} accepted-but-uncommitted
     * candidates. Only ACCEPTED entries are ever committed; already-committed candidates are
     * skipped by the checkpoint cursor and the unique constraint. Stops early when the task
     * is paused or fails.
     */
    public MigrationTask run(Long taskId, int chunkSize, int maxChunks, Integer failAfterCommits) {
        MigrationTask t = mustLoad(taskId);
        if (t.getStatus() == TaskStatus.PENDING) start(taskId);
        for (int i = 0; i < maxChunks; i++) {
            MigrationTask current = mustLoad(taskId);
            if (current.getStatus() != TaskStatus.RUNNING) return current; // paused / failed / completed
            Boolean done;
            try {
                done = tx.execute(status -> processChunk(taskId, chunkSize, failAfterCommits));
            } catch (SimulatedCrash crash) {
                return fail(taskId, crash.getMessage());
            }
            if (Boolean.TRUE.equals(done)) {
                return complete(taskId);
            }
        }
        return mustLoad(taskId);
    }

    /** One chunk = one transaction. Returns TRUE when no work remains. */
    protected boolean processChunk(Long taskId, int chunkSize, Integer failAfterCommits) {
        MigrationTask t = taskRepo.findById(taskId).orElseThrow();
        List<BatchCandidate> chunk = candidateRepo.findAcceptedUncommittedAfter(
                t.getBatchId(), t.getCheckpointCandidateId(), PageRequest.of(0, chunkSize));
        if (chunk.isEmpty()) return true;
        for (BatchCandidate c : chunk) {
            if (failAfterCommits != null && t.getCommittedCount() >= failAfterCommits) {
                throw new SimulatedCrash("simulated service restart after " + t.getCommittedCount() + " commits");
            }
            if (commitRepo.existsByCandidateId(c.getId())) {
                t.setSkippedCount(t.getSkippedCount() + 1);
            } else {
                commitRepo.saveAndFlush(new MigrationCommit(t.getId(), t.getBatchId(), c.getId()));
                c.setStatus(CandidateStatus.MIGRATED);
                candidateRepo.save(c);
                t.setCommittedCount(t.getCommittedCount() + 1);
            }
            t.setCheckpointCandidateId(c.getId());
        }
        t.setProcessedCount(t.getProcessedCount() + chunk.size());
        taskRepo.saveAndFlush(t);
        return false;
    }

    private MigrationTask fail(Long taskId, String message) {
        return tx.execute(s -> {
            MigrationTask t = taskRepo.findById(taskId).orElseThrow();
            t.setError(message);
            transition(t, TaskStatus.FAILED, message);
            return t;
        });
    }

    private MigrationTask complete(Long taskId) {
        MigrationTask t = tx.execute(s -> {
            MigrationTask task = taskRepo.findById(taskId).orElseThrow();
            transition(task, TaskStatus.COMPLETED,
                    "committed=" + task.getCommittedCount() + " skipped=" + task.getSkippedCount());
            return task;
        });
        batchRepo.findById(t.getBatchId()).ifPresent(b -> {
            b.setStatus(BatchStatus.COMPLETED);
            batchRepo.save(b);
        });
        return t;
    }

    private MigrationTask transition(MigrationTask t, TaskStatus to, String message) {
        String from = t.getStatus().name();
        t.setStatus(to);
        MigrationTask saved = taskRepo.save(t);
        eventRepo.save(new TaskEvent(t.getId(), from, to.name(), message));
        return saved;
    }

    public MigrationTask mustLoad(Long taskId) {
        return taskRepo.findById(taskId).orElseThrow(() -> ApiException.notFound("task " + taskId));
    }

    public List<TaskEvent> events(Long taskId) {
        return eventRepo.findByTaskIdOrderByIdAsc(taskId);
    }

    public List<MigrationCommit> commits(Long taskId) {
        return commitRepo.findByTaskId(taskId);
    }
}
