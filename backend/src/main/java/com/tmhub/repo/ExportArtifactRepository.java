package com.tmhub.repo;

import com.tmhub.domain.ExportArtifact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportArtifactRepository extends JpaRepository<ExportArtifact, Long> {
    List<ExportArtifact> findByVersionIdOrderByIdDesc(Long versionId);
}
