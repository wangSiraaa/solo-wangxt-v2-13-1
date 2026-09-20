package com.tmhub.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "review_decision")
public class ReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(nullable = false, length = 100)
    private String reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionAction action;

    @Column(name = "final_target", columnDefinition = "text")
    private String finalTarget;

    @Column(columnDefinition = "text")
    private String rationale;

    @Column(name = "previous_status", nullable = false, length = 20)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;

    @Column(name = "previous_final_target", columnDefinition = "text")
    private String previousFinalTarget;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public DecisionAction getAction() { return action; }
    public void setAction(DecisionAction action) { this.action = action; }
    public String getFinalTarget() { return finalTarget; }
    public void setFinalTarget(String finalTarget) { this.finalTarget = finalTarget; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public String getPreviousFinalTarget() { return previousFinalTarget; }
    public void setPreviousFinalTarget(String previousFinalTarget) { this.previousFinalTarget = previousFinalTarget; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }
}
