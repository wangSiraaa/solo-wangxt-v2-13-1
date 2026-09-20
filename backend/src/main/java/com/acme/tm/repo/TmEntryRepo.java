package com.acme.tm.repo;

import com.acme.tm.model.TmEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TmEntryRepo extends JpaRepository<TmEntry, Long> {
    List<TmEntry> findByVersionId(Long versionId);
    List<TmEntry> findByVersionIdOrderByIdAsc(Long versionId);
}
