package com.tmhub.repo;

import com.tmhub.domain.TmEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TmEntryRepository extends JpaRepository<TmEntry, Long> {
    List<TmEntry> findByVersionIdOrderById(Long versionId);
    Optional<TmEntry> findByVersionIdAndIdentityKey(Long versionId, String identityKey);
    long countByVersionId(Long versionId);
    List<TmEntry> findByVersionIdAndIdGreaterThanOrderById(Long versionId, long id, org.springframework.data.domain.Pageable pageable);
}
