package com.tmhub.service;

import com.tmhub.domain.Candidate;
import com.tmhub.domain.CandidateAnomaly;
import com.tmhub.domain.CandidateStatus;
import com.tmhub.domain.ConflictGroup;
import com.tmhub.domain.DecisionAction;
import com.tmhub.domain.ReviewDecision;
import com.tmhub.repo.CandidateAnomalyRepository;
import com.tmhub.repo.CandidateRepository;
import com.tmhub.repo.ConflictGroupRepository;
import com.tmhub.repo.ReviewDecisionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review decisions.
 *
 * Guarantees:
 *  - the candidate row is locked (SELECT ... FOR UPDATE) for the whole decision, so two reviewers
 *    can never produce a double decision — the loser gets a 409 version-conflict prompt;
 *  - an optional expectedVersion (optimistic token from the UI) is verified before applying;
 *  - decisions are append-only audit rows recording operator, time, rationale and prior state;
 *  - accepting one candidate of a conflict group auto-supersedes the undecided siblings with a
 *    traceable AUTO_SUPERSEDE decision; already-decided siblings are never touched.
 */
@Service
public class ReviewService {

    private final CandidateRepository candidateRepository;
    private final ReviewDecisionRepository decisionRepository;
    private final ConflictGroupRepository conflictGroupRepository;
    private final CandidateAnomalyRepository anomalyRepository;
    private final ValidationService validationService;

    public ReviewService(CandidateRepository candidateRepository,
                         ReviewDecisionRepository decisionRepository,
                         ConflictGroupRepository conflictGroupRepository,
                         CandidateAnomalyRepository anomalyRepository,
                         ValidationService validationService) {
        this.candidateRepository = candidateRepository;
        this.decisionRepository = decisionRepository;
        this.conflictGroupRepository = conflictGroupRepository;
        this.anomalyRepository = anomalyRepository;
        this.validationService = validationService;
    }

    public record DecisionCommand(String reviewer, DecisionAction action,
                                  String finalTarget, String rationale, Long expectedVersion) {}

    @Transactional
    public Candidate decide(long candidateId, DecisionCommand cmd) {
        if (cmd.reviewer() == null || cmd.reviewer().isBlank()) {
            throw new BadRequestException("reviewer is required");
        }
        Candidate candidate = candidateRepository.findByIdForUpdate(candidateId)
                .orElseThrow(() -> new NotFoundException("candidate " + candidateId));

        if (cmd.expectedVersion() != null && cmd.expectedVersion() != candidate.getVersion()) {
            throw new VersionConflictException("candidate " + candidateId + " was modified by someone else (version "
                    + candidate.getVersion() + "); reload before deciding");
        }
        if (candidate.getStatus() == CandidateStatus.ACCEPTED || candidate.getStatus() == CandidateStatus.REJECTED) {
            throw new VersionConflictException("candidate " + candidateId + " is already "
                    + candidate.getStatus() + "; decisions cannot be overwritten");
        }

        String previousStatus = candidate.getStatus().name();
        String previousFinal = candidate.getFinalTarget();

        switch (cmd.action()) {
            case ACCEPT -> applyAccept(candidate, cmd, null);
            case REJECT -> candidate.setStatus(CandidateStatus.REJECTED);
            case SET_FINAL -> {
                if (cmd.finalTarget() == null || cmd.finalTarget().isBlank()) {
                    throw new BadRequestException("finalTarget is required for SET_FINAL");
                }
                // The human-supplied final wording must still be placeholder-safe.
                Placeholders.PlaceholderDiff diff =
                        Placeholders.diff(candidate.getSourceText(), cmd.finalTarget());
                if (!diff.matches()) {
                    throw new BadRequestException("final target does not preserve placeholders/escapes: "
                            + "expected " + diff.sourceTokens() + " got " + diff.targetTokens());
                }
                applyAccept(candidate, cmd, cmd.finalTarget());
            }
            default -> throw new BadRequestException("unsupported action " + cmd.action());
        }
        candidate.setUpdatedAt(OffsetDateTime.now());
        Candidate saved = candidateRepository.save(candidate);

        ReviewDecision decision = new ReviewDecision();
        decision.setCandidateId(saved.getId());
        decision.setReviewer(cmd.reviewer());
        decision.setAction(cmd.action());
        decision.setFinalTarget(cmd.finalTarget());
        decision.setRationale(cmd.rationale());
        decision.setPreviousStatus(previousStatus);
        decision.setNewStatus(saved.getStatus().name());
        decision.setPreviousFinalTarget(previousFinal);
        decisionRepository.save(decision);

        if (saved.getStatus() == CandidateStatus.ACCEPTED) {
            resolveAnomalies(saved, cmd.reviewer());
            supersedeConflictSiblings(saved, cmd);
        }
        return saved;
    }

    private void applyAccept(Candidate candidate, DecisionCommand cmd, String finalTarget) {
        if (finalTarget != null) {
            candidate.setFinalTarget(finalTarget);
        }
        candidate.setStatus(CandidateStatus.ACCEPTED);
    }

    /** Manual handling of quarantined candidates: accepting/resolving closes their anomalies. */
    private void resolveAnomalies(Candidate candidate, String reviewer) {
        List<CandidateAnomaly> open = anomalyRepository.findByCandidateIdAndResolvedFalse(candidate.getId());
        for (CandidateAnomaly anomaly : open) {
            anomaly.setResolved(true);
            anomaly.setResolvedBy(reviewer);
            anomaly.setResolvedAt(OffsetDateTime.now());
            anomaly.setResolution("resolved by reviewer decision");
            anomalyRepository.save(anomaly);
        }
    }

    private void supersedeConflictSiblings(Candidate winner, DecisionCommand cmd) {
        if (winner.getConflictGroupId() == null) {
            return;
        }
        ConflictGroup group = conflictGroupRepository.findById(winner.getConflictGroupId()).orElseThrow();
        for (Candidate sibling : candidateRepository.findByConflictGroupIdOrderById(group.getId())) {
            if (sibling.getId().equals(winner.getId())) {
                continue;
            }
            if (sibling.getStatus() == CandidateStatus.PENDING
                    || sibling.getStatus() == CandidateStatus.CONFLICT) {
                String prev = sibling.getStatus().name();
                sibling.setStatus(CandidateStatus.REJECTED);
                sibling.setUpdatedAt(OffsetDateTime.now());
                candidateRepository.save(sibling);

                ReviewDecision auto = new ReviewDecision();
                auto.setCandidateId(sibling.getId());
                auto.setReviewer("system");
                auto.setAction(DecisionAction.AUTO_SUPERSEDE);
                auto.setRationale("superseded by candidate " + winner.getId()
                        + " chosen by " + cmd.reviewer()
                        + (cmd.rationale() != null ? ": " + cmd.rationale() : ""));
                auto.setPreviousStatus(prev);
                auto.setNewStatus(CandidateStatus.REJECTED.name());
                decisionRepository.save(auto);
            }
        }
        group.setStatus(ConflictGroup.Status.RESOLVED);
        group.setResolvedCandidateId(winner.getId());
        group.setResolvedBy(cmd.reviewer());
        group.setResolvedAt(OffsetDateTime.now());
        group.setResolutionNote(cmd.rationale());
        conflictGroupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public List<ReviewDecision> historyOf(long candidateId) {
        return decisionRepository.findByCandidateIdOrderByDecidedAtAsc(candidateId);
    }
}
