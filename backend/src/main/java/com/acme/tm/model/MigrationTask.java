package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "migration_task")
public class MigrationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "checkpoint_candidate_id", nullable = false)
    private long checkpointCandidateId = 0;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "committed_count", nullable = false)
    private int committedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(length = 2000)
    private String error;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MigrationTask() {}

    public MigrationTask(Long batchId) {
        this.batchId = batchId;
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getBatchId() { return batchId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public long getCheckpointCandidateId() { return checkpointCandidateId; }
    public void setCheckpointCandidateId(long checkpointCandidateId) { this.checkpointCandidateId = checkpointCandidateId; }
    public int getProcessedCount() { return processedCount; }
    public void setProcessedCount(int processedCount) { this.processedCount = processedCount; }
    public int getCommittedCount() { return committedCount; }
    public void setCommittedCount(int committedCount) { this.committedCount = committedCount; }
    public int getSkippedCount() { return skippedCount; }
    public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
