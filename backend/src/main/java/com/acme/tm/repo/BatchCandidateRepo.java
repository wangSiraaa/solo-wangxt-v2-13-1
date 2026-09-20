package com.acme.tm.repo;

import com.acme.tm.model.BatchCandidate;
import com.acme.tm.model.CandidateStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BatchCandidateRepo extends JpaRepository<BatchCandidate, Long> {
    List<BatchCandidate> findByBatchIdOrderByIdAsc(Long batchId);
    List<BatchCandidate> findByBatchIdAndStatus(Long batchId, CandidateStatus status);
    long countByBatchId(Long batchId);
    long countByBatchIdAndStatus(Long batchId, CandidateStatus status);
    List<BatchCandidate> findByConflictGroupIdOrderByIdAsc(Long conflictGroupId);

    @Query("select c from BatchCandidate c where c.sourceLang = :sl and c.targetLang = :tl " +
           "and c.productLine = :pl and c.sourceHash = :sh and c.batchId <> :batchId")
    List<BatchCandidate> findSameSourceInOtherBatches(@Param("sl") String sourceLang,
                                                      @Param("tl") String targetLang,
                                                      @Param("pl") String productLine,
                                                      @Param("sh") String sourceHash,
                                                      @Param("batchId") Long batchId);

    @Query("select c from BatchCandidate c where c.batchId = :batchId and c.status = 'ACCEPTED' " +
           "and c.id > :afterId and not exists (select 1 from MigrationCommit m where m.candidateId = c.id) " +
           "order by c.id asc")
    List<BatchCandidate> findAcceptedUncommittedAfter(@Param("batchId") Long batchId,
                                                      @Param("afterId") long afterId,
                                                      Pageable pageable);
}
