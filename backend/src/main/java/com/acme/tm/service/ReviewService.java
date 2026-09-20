package com.acme.tm.service;

import com.acme.tm.error.ApiException;
import com.acme.tm.model.*;
import com.acme.tm.repo.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReviewService {
    private final BatchCandidateRepo candidateRepo;
    private final ReviewDecisionRepo decisionRepo;
    private final ConflictGroupRepo conflictRepo;
    private final TermMappingRuleRepo ruleRepo;
    private final LanguageProfileRepo profileRepo;

    public ReviewService(BatchCandidateRepo candidateRepo, ReviewDecisionRepo decisionRepo,
                         ConflictGroupRepo conflictRepo, TermMappingRuleRepo ruleRepo,
                         LanguageProfileRepo profileRepo) {
        this.candidateRepo = candidateRepo;
        this.decisionRepo = decisionRepo;
        this.conflictRepo = conflictRepo;
        this.ruleRepo = ruleRepo;
        this.profileRepo = profileRepo;
    }

    /**
     * Records a review decision. Decisions are immutable: a candidate that already has an
     * effective decision rejects further attempts (409), so retries can never overwrite a
     * decision. Concurrent reviewers are serialized by the optimistic version check — the
     * loser gets a 409 version-conflict instead of a double review result.
     */
    @Transactional
    public BatchCandidate decide(Long candidateId, String reviewer, ReviewAction action,
                                 String termOverride, String correctedTarget, String reason,
                                 long expectedVersion) {
        BatchCandidate c = candidateRepo.findById(candidateId)
                .orElseThrow(() -> ApiException.notFound("candidate " + candidateId));

        if (c.getVersion() != expectedVersion) {
            throw ApiException.conflict("candidate " + candidateId + " was modified by someone else: expected version "
                    + expectedVersion + " but current is " + c.getVersion() + " (status " + c.getStatus() + ")");
        }
        if (c.getStatus() == CandidateStatus.ACCEPTED || c.getStatus() == CandidateStatus.REJECTED
                || c.getStatus() == CandidateStatus.MIGRATED) {
            throw ApiException.conflict("candidate " + candidateId + " already decided as " + c.getStatus()
                    + " by " + c.getDecidedBy() + "; decisions are immutable");
        }
        if (decisionRepo.findByCandidateId(candidateId).isPresent()) {
            throw ApiException.conflict("candidate " + candidateId + " already has a review decision");
        }

        long priorVersion = c.getVersion();
        switch (action) {
            case REJECT -> c.setStatus(CandidateStatus.REJECTED);
            case ACCEPT, RESOLVE -> applyAccept(c, termOverride, correctedTarget);
        }

        c.setDecidedBy(reviewer);
        c.setDecidedAt(Instant.now());
        if (termOverride != null && !termOverride.isBlank()) c.setTermOverride(termOverride);

        try {
            ReviewDecision decision = new ReviewDecision(candidateId, reviewer, action, termOverride,
                    correctedTarget, reason, priorVersion);
            decisionRepo.saveAndFlush(decision);
            BatchCandidate saved = candidateRepo.saveAndFlush(c);
            resolveConflictGroup(saved, action);
            return saved;
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw ApiException.conflict("candidate " + candidateId
                    + " was decided concurrently; only one review result is kept");
        }
    }

    private void applyAccept(BatchCandidate c, String termOverride, String correctedTarget) {
        if (c.getStatus() == CandidateStatus.BLOCKED) {
            resolveAnomaly(c, termOverride, correctedTarget);
        } else if (correctedTarget != null && !correctedTarget.isBlank()) {
            applyCorrectedTarget(c, correctedTarget);
        } else if (termOverride != null && !termOverride.isBlank()) {
            applyTermOverride(c, termOverride);
        }
        c.setStatus(CandidateStatus.ACCEPTED);
    }

    /** A blocked candidate may only be accepted once its anomaly is explicitly resolved. */
    private void resolveAnomaly(BatchCandidate c, String termOverride, String correctedTarget) {
        switch (c.getAnomalyType()) {
            case PLACEHOLDER_MISMATCH -> {
                if (correctedTarget == null || correctedTarget.isBlank()) {
                    throw ApiException.unprocessable("placeholder anomaly requires a corrected target; "
                            + c.getAnomalyDetail());
                }
                applyCorrectedTarget(c, correctedTarget);
            }
            case POLYSEMOUS_TERM -> {
                if (termOverride == null || termOverride.isBlank()) {
                    throw ApiException.unprocessable("polysemous term requires an explicit human choice; "
                            + c.getAnomalyDetail());
                }
                assertOverrideIsApproved(c, termOverride);
                if (correctedTarget != null && !correctedTarget.isBlank()) {
                    applyCorrectedTarget(c, correctedTarget);
                } else {
                    applyTermOverride(c, termOverride);
                }
            }
            case CASE_VIOLATION -> {
                if (correctedTarget == null || correctedTarget.isBlank()) {
                    throw ApiException.unprocessable("case violation requires a corrected target; "
                            + c.getAnomalyDetail());
                }
                applyCorrectedTarget(c, correctedTarget);
                verifyCaseRule(c);
            }
        }
        c.setAnomalyType(null);
        c.setAnomalyDetail(null);
    }

    private void applyCorrectedTarget(BatchCandidate c, String correctedTarget) {
        if (!Placeholders.preserved(c.getSourceText(), correctedTarget)) {
            throw ApiException.unprocessable("corrected target still violates placeholder preservation: "
                    + Placeholders.diff(c.getSourceText(), correctedTarget));
        }
        c.setTargetText(correctedTarget);
        c.setPlaceholderSig(Placeholders.signature(correctedTarget));
    }

    private void applyTermOverride(BatchCandidate c, String termOverride) {
        List<TermMappingRule> rules = ruleRepo.findBySourceLangAndTargetLangAndProductLine(
                c.getSourceLang(), c.getTargetLang(), c.getProductLine());
        String target = c.getTargetText();
        for (TermMappingRule r : rules) {
            if (!r.isPolysemous() || !c.getSourceText().contains(r.getSourceTerm())) continue;
            String replaced = target;
            for (String alt : r.alternativeList()) {
                replaced = replaceIgnoreCase(replaced, alt, termOverride);
            }
            if (!replaced.equals(target)) {
                c.setTargetText(replaced);
                return;
            }
        }
        throw ApiException.unprocessable("term override '" + termOverride
                + "' could not be applied to the target; supply a corrected target instead");
    }

    private void assertOverrideIsApproved(BatchCandidate c, String termOverride) {
        List<TermMappingRule> rules = ruleRepo.findBySourceLangAndTargetLangAndProductLine(
                c.getSourceLang(), c.getTargetLang(), c.getProductLine());
        for (TermMappingRule r : rules) {
            if (r.isPolysemous() && c.getSourceText().contains(r.getSourceTerm())) {
                if (r.alternativeList().contains(termOverride)) return;
                throw ApiException.unprocessable("term override '" + termOverride
                        + "' is not an approved alternative: " + r.getAlternatives());
            }
        }
    }

    private void verifyCaseRule(BatchCandidate c) {
        CaseRule caseRule = profileRepo.findByCode(c.getTargetLang())
                .map(LanguageProfile::getCaseRule).orElse(CaseRule.PRESERVE);
        List<TermMappingRule> rules = ruleRepo.findBySourceLangAndTargetLangAndProductLine(
                c.getSourceLang(), c.getTargetLang(), c.getProductLine());
        for (TermMappingRule r : rules) {
            if (r.isPolysemous() || !c.getSourceText().contains(r.getSourceTerm())) continue;
            String expected = caseRule.apply(r.getApprovedTarget());
            if (c.getTargetText().toLowerCase(Locale.ROOT).contains(r.getApprovedTarget().toLowerCase(Locale.ROOT))
                    && !c.getTargetText().contains(expected)) {
                throw ApiException.unprocessable("corrected target still violates case rule: expected '" + expected + "'");
            }
        }
    }

    private void resolveConflictGroup(BatchCandidate c, ReviewAction action) {
        if (c.getConflictGroupId() == null) return;
        if (action != ReviewAction.ACCEPT && action != ReviewAction.RESOLVE) return;
        conflictRepo.findById(c.getConflictGroupId()).ifPresent(g -> {
            g.setStatus("RESOLVED");
            g.setResolvedCandidateId(c.getId());
            conflictRepo.save(g);
        });
    }

    public List<ReviewDecision> history(List<Long> candidateIds) {
        return decisionRepo.findByCandidateIdIn(candidateIds);
    }

    private static String replaceIgnoreCase(String text, String term, String replacement) {
        return text.replaceAll("(?i)" + Pattern.quote(term), Matcher.quoteReplacement(replacement));
    }
}
