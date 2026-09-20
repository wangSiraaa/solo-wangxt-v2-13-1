package com.acme.tm.repo;

import com.acme.tm.model.ConflictGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConflictGroupRepo extends JpaRepository<ConflictGroup, Long> {
    Optional<ConflictGroup> findBySourceHashAndSourceLangAndTargetLangAndProductLine(
            String sourceHash, String sourceLang, String targetLang, String productLine);
}
