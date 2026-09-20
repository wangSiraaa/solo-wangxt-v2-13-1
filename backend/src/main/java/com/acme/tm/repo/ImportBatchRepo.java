package com.acme.tm.repo;

import com.acme.tm.model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportBatchRepo extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByIdempotencyKey(String idempotencyKey);
}
