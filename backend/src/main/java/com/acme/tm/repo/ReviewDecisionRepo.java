package com.acme.tm.repo;

import com.acme.tm.model.ReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewDecisionRepo extends JpaRepository<ReviewDecision, Long> {
    Optional<ReviewDecision> findByCandidateId(Long candidateId);
    List<ReviewDecision> findByCandidateIdIn(List<Long> candidateIds);
}
