package com.tmhub.repo;

import com.tmhub.domain.VersionBatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionBatchRepository extends JpaRepository<VersionBatch, VersionBatch.VersionBatchId> {
    List<VersionBatch> findByVersionId(Long versionId);
}
