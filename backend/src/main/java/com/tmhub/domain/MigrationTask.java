package com.tmhub.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "migration_task")
public class MigrationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(nullable = false)
    private int total = 0;

    @Column(name = "committed_count", nullable = false)
    private int committedCount = 0;

    /** Checkpoint: only candidates with id greater than this are examined on resume. */
    @Column(name = "last_candidate_id", nullable = false)
    private long lastCandidateId = 0;

    @Column(columnDefinition = "text")
    private String error;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getCommittedCount() { return committedCount; }
    public void setCommittedCount(int committedCount) { this.committedCount = committedCount; }
    public long getLastCandidateId() { return lastCandidateId; }
    public void setLastCandidateId(long lastCandidateId) { this.lastCandidateId = lastCandidateId; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
