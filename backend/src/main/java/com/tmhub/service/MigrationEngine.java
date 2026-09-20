package com.tmhub.service;

import com.tmhub.domain.Batch;
import com.tmhub.domain.BatchStatus;
import com.tmhub.domain.Candidate;
import com.tmhub.domain.CandidateStatus;
import com.tmhub.domain.MigrationCommit;
import com.tmhub.domain.MigrationTask;
import com.tmhub.domain.TaskStatus;
import com.tmhub.repo.BatchRepository;
import com.tmhub.repo.CandidateRepository;
import com.tmhub.repo.MigrationCommitRepository;
import com.tmhub.repo.MigrationTaskRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkpointed migration engine.
 *
 * A task walks the candidates of one batch in id order, committing only ACCEPTED, not-yet-committed
 * candidates. Each chunk commits in its own transaction and persists the checkpoint
 * (last_candidate_id + committed_count) in the same transaction, so a crash anywhere rolls back to
 * the previous chunk boundary and a resume never re-commits. migration_commit.candidate_id is
 * UNIQUE, which makes a double commit impossible even across tasks.
 */
@Service
public class MigrationEngine {

    private final MigrationTaskRepository taskRepository;
    private final CandidateRepository candidateRepository;
    private final MigrationCommitRepository commitRepository;
    private final BatchRepository batchRepository;
    private final int chunkSize;
    private final java.util.function.Supplier<MigrationEngine> self;

    public MigrationEngine(MigrationTaskRepository taskRepository,
                           CandidateRepository candidateRepository,
                           MigrationCommitRepository commitRepository,
                           BatchRepository batchRepository,
                           @Value("${tmhub.migration.chunk-size:25}") int chunkSize,
                           @org.springframework.context.annotation.Lazy MigrationEngine self) {
        this.taskRepository = taskRepository;
        this.candidateRepository = candidateRepository;
        this.commitRepository = commitRepository;
        this.batchRepository = batchRepository;
        this.chunkSize = chunkSize;
        this.self = () -> self;
    }

    @Transactional
    public MigrationTask createTask(long batchId, String name) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new NotFoundException("batch " + batchId));
        if (batch.getStatus() != BatchStatus.READY) {
            throw new BadRequestException("batch " + batchId + " is " + batch.getStatus() + ", not READY");
        }
        MigrationTask task = new MigrationTask();
        task.setBatchId(batchId);
        task.setName(name != null ? name : "migrate-batch-" + batchId);
        task.setTotal((int) candidateRepository.countByBatchIdAndStatus(batchId, CandidateStatus.ACCEPTED));
        task.setStatus(TaskStatus.PENDING);
        return taskRepository.save(task);
    }

    @Transactional
    public MigrationTask start(long taskId) {
        MigrationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("task " + taskId));
        if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.COMPLETED) {
            return task; // idempotent: re-starting a finished or running task changes nothing
        }
        task.setStatus(TaskStatus.RUNNING);
        task.setError(null);
        task.setUpdatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional
    public MigrationTask pause(long taskId) {
        MigrationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("task " + taskId));
        if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.PENDING) {
            task.setStatus(TaskStatus.PAUSED);
            task.setUpdatedAt(OffsetDateTime.now());
        }
        return taskRepository.save(task);
    }

    /** Resume from the persisted checkpoint; works after PAUSED, FAILED and INTERRUPTED. */
    public MigrationTask resume(long taskId) {
        self.get().start(taskId);
        return run(taskId);
    }

    /** Run chunk by chunk until done, paused or failed. */
    public MigrationTask run(long taskId) {
        while (true) {
            MigrationTask task;
            try {
                task = self.get().processChunk(taskId);
            } catch (RuntimeException e) {
                // The failed chunk rolled back with its checkpoint; record the failure separately.
                return self.get().markFailed(taskId, e.getMessage());
            }
            if (task.getStatus() != TaskStatus.RUNNING) {
                return task;
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MigrationTask markFailed(long taskId, String error) {
        MigrationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("task " + taskId));
        task.setStatus(TaskStatus.FAILED);
        task.setError(error);
        task.setUpdatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    /**
     * Process one chunk in its own transaction. Returns the updated task.
     * The checkpoint moves forward only together with the commits of the same chunk.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MigrationTask processChunk(long taskId) {
        MigrationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("task " + taskId));
        if (task.getStatus() != TaskStatus.RUNNING) {
            return task; // paused / failed / interrupted / completed: nothing to do
        }
        List<Candidate> chunk = candidateRepository.findByBatchIdAndIdGreaterThanOrderById(
                task.getBatchId(), task.getLastCandidateId(), PageRequest.of(0, chunkSize));
        if (chunk.isEmpty()) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setUpdatedAt(OffsetDateTime.now());
            return taskRepository.save(task);
        }
        int committed = 0;
        long maxId = task.getLastCandidateId();
        for (Candidate candidate : chunk) {
            maxId = Math.max(maxId, candidate.getId());
            if (candidate.getStatus() != CandidateStatus.ACCEPTED || candidate.isCommitted()) {
                continue; // only accepted, not-yet-committed entries are migrated
            }
            if (commitRepository.existsByCandidateId(candidate.getId())) {
                candidate.setCommitted(true); // committed by another task; mark and skip
                candidateRepository.save(candidate);
                continue;
            }
            MigrationCommit commit = new MigrationCommit();
            commit.setTaskId(taskId);
            commit.setCandidateId(candidate.getId());
            commitRepository.save(commit);
            candidate.setCommitted(true);
            candidateRepository.save(candidate);
            committed++;
        }
        task.setLastCandidateId(maxId);
        task.setCommittedCount(task.getCommittedCount() + committed);
        task.setTotal((int) candidateRepository.countByBatchIdAndStatus(
                task.getBatchId(), CandidateStatus.ACCEPTED));
        task.setUpdatedAt(OffsetDateTime.now());
        return taskRepository.save(task);
    }

    /**
     * Crash recovery: tasks left RUNNING by a dead process become INTERRUPTED with their
     * checkpoint intact; resume() continues exactly after the last committed chunk.
     */
    @Transactional
    public int recoverInterruptedTasks() {
        int recovered = 0;
        for (MigrationTask task : taskRepository.findByStatus(TaskStatus.RUNNING)) {
            task.setStatus(TaskStatus.INTERRUPTED);
            task.setError("process interrupted; checkpoint at candidate " + task.getLastCandidateId());
            task.setUpdatedAt(OffsetDateTime.now());
            taskRepository.save(task);
            recovered++;
        }
        return recovered;
    }

    @Transactional(readOnly = true)
    public MigrationTask get(long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("task " + taskId));
    }

    @Transactional(readOnly = true)
    public List<MigrationTask> list() {
        return taskRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<MigrationCommit> commitsOf(long taskId) {
        return commitRepository.findByTaskIdOrderById(taskId);
    }
}
