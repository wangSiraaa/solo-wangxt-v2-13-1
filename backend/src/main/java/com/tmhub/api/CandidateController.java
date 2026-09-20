package com.tmhub.api;

import com.tmhub.domain.Candidate;
import com.tmhub.domain.CandidateAnomaly;
import com.tmhub.domain.ConflictGroup;
import com.tmhub.domain.ReviewDecision;
import com.tmhub.repo.CandidateAnomalyRepository;
import com.tmhub.repo.CandidateRepository;
import com.tmhub.repo.ConflictGroupRepository;
import com.tmhub.service.NotFoundException;
import com.tmhub.service.Placeholders;
import com.tmhub.service.ReviewService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CandidateController {

    private final CandidateRepository candidateRepository;
    private final CandidateAnomalyRepository anomalyRepository;
    private final ConflictGroupRepository conflictGroupRepository;
    private final ReviewService reviewService;

    public CandidateController(CandidateRepository candidateRepository,
                               CandidateAnomalyRepository anomalyRepository,
                               ConflictGroupRepository conflictGroupRepository,
                               ReviewService reviewService) {
        this.candidateRepository = candidateRepository;
        this.anomalyRepository = anomalyRepository;
        this.conflictGroupRepository = conflictGroupRepository;
        this.reviewService = reviewService;
    }

    @GetMapping("/batches/{batchId}/candidates")
    public List<Candidate> ofBatch(@PathVariable long batchId) {
        return candidateRepository.findByBatchIdOrderById(batchId);
    }

    public record CandidateDetail(Candidate candidate,
                                  List<CandidateAnomaly> anomalies,
                                  List<ReviewDecision> decisions,
                                  Placeholders.PlaceholderDiff placeholderDiff,
                                  List<Candidate> conflictChain) {}

    @GetMapping("/candidates/{id}")
    public CandidateDetail detail(@PathVariable long id) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("candidate " + id));
        List<Candidate> chain = candidate.getConflictGroupId() != null
                ? candidateRepository.findByConflictGroupIdOrderById(candidate.getConflictGroupId())
                : List.of();
        return new CandidateDetail(candidate,
                anomalyRepository.findByCandidateId(id),
                reviewService.historyOf(id),
                Placeholders.diff(candidate.getSourceText(), candidate.effectiveTarget()),
                chain);
    }

    public record DecisionRequest(String reviewer, String action, String finalTarget,
                                  String rationale, Long expectedVersion) {}

    /**
     * Accept / reject / set-final. Pessimistic lock + expectedVersion token:
     * a second concurrent reviewer gets HTTP 409 version_conflict, never a double decision.
     */
    @PostMapping("/candidates/{id}/decisions")
    public Candidate decide(@PathVariable long id, @RequestBody DecisionRequest req) {
        return reviewService.decide(id, new ReviewService.DecisionCommand(
                req.reviewer(),
                com.tmhub.domain.DecisionAction.valueOf(req.action()),
                req.finalTarget(), req.rationale(), req.expectedVersion()));
    }

    @GetMapping("/candidates/{id}/decisions")
    public List<ReviewDecision> decisions(@PathVariable long id) {
        return reviewService.historyOf(id);
    }

    @GetMapping("/conflicts/{groupId}")
    public ConflictGroup conflict(@PathVariable long groupId) {
        return conflictGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("conflict group " + groupId));
    }

    @GetMapping("/conflicts/{groupId}/candidates")
    public List<Candidate> conflictCandidates(@PathVariable long groupId) {
        return candidateRepository.findByConflictGroupIdOrderById(groupId);
    }
}
