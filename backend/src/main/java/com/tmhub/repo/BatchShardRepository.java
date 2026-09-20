package com.tmhub.repo;

import com.tmhub.domain.BatchShard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchShardRepository extends JpaRepository<BatchShard, BatchShard.BatchShardId> {
    List<BatchShard> findByBatchIdOrderByShardIndex(Long batchId);
    long countByBatchId(Long batchId);
}
