package com.acme.tm.repo;

import com.acme.tm.model.TmVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TmVersionRepo extends JpaRepository<TmVersion, Long> {
    Optional<TmVersion> findByEffectiveTrue();
    List<TmVersion> findAllByOrderByIdAsc();
}
