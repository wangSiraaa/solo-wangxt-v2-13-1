package com.tmhub.repo;

import com.tmhub.domain.MigrationCommit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationCommitRepository extends JpaRepository<MigrationCommit, Long> {
    boolean existsByCandidateId(Long candidateId);
    List<MigrationCommit> findByTaskIdOrderById(Long taskId);
}
