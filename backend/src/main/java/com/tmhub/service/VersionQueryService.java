package com.tmhub.service;

import com.tmhub.domain.TmVersion;
import com.tmhub.domain.VersionBatch;
import com.tmhub.domain.VersionConflictResolution;
import com.tmhub.domain.VersionPointer;
import com.tmhub.repo.TmVersionRepository;
import com.tmhub.repo.VersionBatchRepository;
import com.tmhub.repo.VersionConflictResolutionRepository;
import com.tmhub.repo.VersionPointerRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VersionQueryService {

    private final TmVersionRepository versionRepository;
    private final VersionBatchRepository versionBatchRepository;
    private final VersionConflictResolutionRepository conflictRepository;
    private final VersionPointerRepository pointerRepository;

    public VersionQueryService(TmVersionRepository versionRepository,
                               VersionBatchRepository versionBatchRepository,
                               VersionConflictResolutionRepository conflictRepository,
                               VersionPointerRepository pointerRepository) {
        this.versionRepository = versionRepository;
        this.versionBatchRepository = versionBatchRepository;
        this.conflictRepository = conflictRepository;
        this.pointerRepository = pointerRepository;
    }

    public record VersionNode(TmVersion version, List<Long> batchIds,
                              List<VersionConflictResolution> conflictResolutions,
                              boolean effective) {}

    public record Lineage(List<VersionNode> chain, Long effectiveVersionId) {}

    /** Full ancestry chain from the given version back to the root, plus release metadata. */
    @Transactional(readOnly = true)
    public Lineage lineage(long versionId) {
        Long effectiveId = pointerRepository.findById((short) 1).map(VersionPointer::getVersionId).orElse(null);
        List<VersionNode> chain = new ArrayList<>();
        Long cursor = versionId;
        while (cursor != null) {
            final Long currentId = cursor;
            TmVersion version = versionRepository.findById(currentId)
                    .orElseThrow(() -> new NotFoundException("version " + currentId));
            List<Long> batchIds = versionBatchRepository.findByVersionId(version.getId()).stream()
                    .map(VersionBatch::getBatchId).toList();
            List<VersionConflictResolution> conflicts = conflictRepository.findByVersionId(version.getId());
            chain.add(new VersionNode(version, batchIds, conflicts, version.getId().equals(effectiveId)));
            cursor = version.getParentId();
        }
        return new Lineage(chain, effectiveId);
    }

    @Transactional(readOnly = true)
    public List<TmVersion> allVersions() {
        return versionRepository.findAll();
    }
}
