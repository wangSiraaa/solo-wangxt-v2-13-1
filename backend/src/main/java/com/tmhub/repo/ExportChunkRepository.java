package com.tmhub.repo;

import com.tmhub.domain.ExportChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportChunkRepository extends JpaRepository<ExportChunk, Long> {
    List<ExportChunk> findByExportTaskIdOrderBySeq(Long exportTaskId);
}
