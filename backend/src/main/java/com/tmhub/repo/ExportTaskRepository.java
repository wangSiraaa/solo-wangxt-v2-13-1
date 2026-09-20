package com.tmhub.repo;

import com.tmhub.domain.ExportTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {
    List<ExportTask> findByVersionIdOrderByIdDesc(Long versionId);
    List<ExportTask> findByStatus(com.tmhub.domain.ExportStatus status);
}
