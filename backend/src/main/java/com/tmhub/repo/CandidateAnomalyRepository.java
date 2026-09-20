package com.tmhub.repo;

import com.tmhub.domain.CandidateAnomaly;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateAnomalyRepository extends JpaRepository<CandidateAnomaly, Long> {
    List<CandidateAnomaly> findByCandidateId(Long candidateId);
    List<CandidateAnomaly> findByCandidateIdAndResolvedFalse(Long candidateId);
}
