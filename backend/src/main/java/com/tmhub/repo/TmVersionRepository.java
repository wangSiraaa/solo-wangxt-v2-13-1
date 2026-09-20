package com.tmhub.repo;

import com.tmhub.domain.TmVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TmVersionRepository extends JpaRepository<TmVersion, Long> {
}
