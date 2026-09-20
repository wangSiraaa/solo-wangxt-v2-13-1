package com.acme.tm.repo;

import com.acme.tm.model.MigrationCommit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MigrationCommitRepo extends JpaRepository<MigrationCommit, Long> {
    boolean existsByCandidateId(Long candidateId);
    List<MigrationCommit> findByTaskId(Long taskId);
    List<MigrationCommit> findByBatchId(Long batchId);
    long countByBatchId(Long batchId);
}
