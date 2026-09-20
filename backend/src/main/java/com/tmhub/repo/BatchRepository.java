package com.tmhub.repo;

import com.tmhub.domain.Batch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    Optional<Batch> findByIdempotencyKey(String idempotencyKey);
}
