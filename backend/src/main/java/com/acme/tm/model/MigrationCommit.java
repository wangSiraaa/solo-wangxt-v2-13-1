package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "migration_commit")
public class MigrationCommit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "candidate_id", nullable = false, unique = true)
    private Long candidateId;

    @Column(name = "committed_at", nullable = false)
    private Instant committedAt = Instant.now();

    protected MigrationCommit() {}

    public MigrationCommit(Long taskId, Long batchId, Long candidateId) {
        this.taskId = taskId;
        this.batchId = batchId;
        this.candidateId = candidateId;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getBatchId() { return batchId; }
    public Long getCandidateId() { return candidateId; }
    public Instant getCommittedAt() { return committedAt; }
}
