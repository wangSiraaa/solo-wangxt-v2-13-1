package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only audit record. At most one effective decision per candidate (unique constraint). */
@Entity
@Table(name = "review_decision")
public class ReviewDecision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false, unique = true)
    private Long candidateId;

    @Column(nullable = false, length = 100)
    private String reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewAction action;

    @Column(name = "term_override", length = 500)
    private String termOverride;

    @Lob
    @Column(name = "corrected_target", columnDefinition = "TEXT")
    private String correctedTarget;

    @Column(length = 2000)
    private String reason;

    @Column(name = "prior_version", nullable = false)
    private long priorVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ReviewDecision() {}

    public ReviewDecision(Long candidateId, String reviewer, ReviewAction action, String termOverride,
                          String correctedTarget, String reason, long priorVersion) {
        this.candidateId = candidateId;
        this.reviewer = reviewer;
        this.action = action;
        this.termOverride = termOverride;
        this.correctedTarget = correctedTarget;
        this.reason = reason;
        this.priorVersion = priorVersion;
    }

    public Long getId() { return id; }
    public Long getCandidateId() { return candidateId; }
    public String getReviewer() { return reviewer; }
    public ReviewAction getAction() { return action; }
    public String getTermOverride() { return termOverride; }
    public String getCorrectedTarget() { return correctedTarget; }
    public String getReason() { return reason; }
    public long getPriorVersion() { return priorVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
