package com.acme.tm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "conflict_group")
public class ConflictGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "source_lang", nullable = false, length = 20)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 20)
    private String targetLang;

    @Column(name = "product_line", nullable = false, length = 100)
    private String productLine;

    @Column(nullable = false, length = 20)
    private String status = "OPEN"; // OPEN | RESOLVED

    @Column(name = "resolved_candidate_id")
    private Long resolvedCandidateId;

    protected ConflictGroup() {}

    public ConflictGroup(String sourceHash, String sourceLang, String targetLang, String productLine) {
        this.sourceHash = sourceHash;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.productLine = productLine;
    }

    public Long getId() { return id; }
    public String getSourceHash() { return sourceHash; }
    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public String getProductLine() { return productLine; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getResolvedCandidateId() { return resolvedCandidateId; }
    public void setResolvedCandidateId(Long resolvedCandidateId) { this.resolvedCandidateId = resolvedCandidateId; }
}
