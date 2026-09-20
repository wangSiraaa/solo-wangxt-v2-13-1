package com.acme.tm.repo;

import com.acme.tm.model.UploadChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadChunkRepo extends JpaRepository<UploadChunk, Long> {
    List<UploadChunk> findBySessionIdOrderByChunkIndexAsc(Long sessionId);
    Optional<UploadChunk> findBySessionIdAndChunkIndex(Long sessionId, int chunkIndex);
    long countBySessionId(Long sessionId);
}
