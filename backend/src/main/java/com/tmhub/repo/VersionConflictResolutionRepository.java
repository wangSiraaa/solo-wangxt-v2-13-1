package com.tmhub.repo;

import com.tmhub.domain.VersionConflictResolution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionConflictResolutionRepository extends JpaRepository<VersionConflictResolution, Long> {
    List<VersionConflictResolution> findByVersionId(Long versionId);
}
