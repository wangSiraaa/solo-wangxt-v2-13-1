package com.tmhub.repo;

import com.tmhub.domain.VersionPointerHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionPointerHistoryRepository extends JpaRepository<VersionPointerHistory, Long> {
}
