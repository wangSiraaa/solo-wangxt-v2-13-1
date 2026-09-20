package com.tmhub.repo;

import com.tmhub.domain.ConflictGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConflictGroupRepository extends JpaRepository<ConflictGroup, Long> {
    Optional<ConflictGroup> findByIdentityKey(String identityKey);
}
