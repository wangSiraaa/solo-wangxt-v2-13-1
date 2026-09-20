package com.tmhub.repo;

import com.tmhub.domain.Candidate;
import com.tmhub.domain.CandidateStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    Optional<Candidate> findByProposalKey(String proposalKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Candidate c where c.id = :id")
    Optional<Candidate> findByIdForUpdate(@Param("id") Long id);

    List<Candidate> findByIdentityKey(String identityKey);

    List<Candidate> findByGroupKeyOrderById(String groupKey);

    List<Candidate> findByBatchIdOrderById(Long batchId);

    List<Candidate> findByBatchIdAndIdGreaterThanOrderById(Long batchId, long id, Pageable pageable);

    List<Candidate> findByConflictGroupIdOrderById(Long conflictGroupId);

    long countByBatchIdAndStatus(Long batchId, CandidateStatus status);

    List<Candidate> findByBatchIdAndStatus(Long batchId, CandidateStatus status);

    List<Candidate> findByBatchIdInAndStatus(List<Long> batchIds, CandidateStatus status);
}
