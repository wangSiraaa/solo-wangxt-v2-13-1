package com.tmhub.repo;

import com.tmhub.domain.ReviewDecision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewDecisionRepository extends JpaRepository<ReviewDecision, Long> {
    List<ReviewDecision> findByCandidateIdOrderByDecidedAtAsc(Long candidateId);
}
