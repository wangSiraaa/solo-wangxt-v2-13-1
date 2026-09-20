package com.tmhub.repo;

import com.tmhub.domain.MigrationTask;
import com.tmhub.domain.TaskStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationTaskRepository extends JpaRepository<MigrationTask, Long> {
    List<MigrationTask> findByStatus(TaskStatus status);
    List<MigrationTask> findByBatchId(Long batchId);
}
