package com.acme.tm.repo;

import com.acme.tm.model.MigrationTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MigrationTaskRepo extends JpaRepository<MigrationTask, Long> {
    List<MigrationTask> findByBatchId(Long batchId);
}
