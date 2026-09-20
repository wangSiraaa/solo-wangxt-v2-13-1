package com.tmhub.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "conflict_group_id")
    private Long conflictGroupId;

    @Column(name = "source_lang", nullable = false, length = 10)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 10)
    private String targetLang;

    @Column(name = "product_line", nullable = false, length = 50)
    private String productLine;

    @Column(nullable = false, length = 100)
    private String vendor;

    @Column(name = "source_text", nullable = false, columnDefinition = "text")
    private String sourceText;

    @Column(name = "proposed_target", nullable = false, columnDefinition = "text")
    private String proposedTarget;

    @Column(name = "final_target", columnDefinition = "text")
    private String finalTarget;

    @Column(name = "identity_key", nullable = false, length = 64)
    private String identityKey;

    @Column(name = "group_key", nullable = false, length = 64)
    private String groupKey;

    @Column(name = "proposal_key", nullable = false, unique = true, length = 64)
    private String proposalKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateStatus status = CandidateStatus.PENDING;

    @Column(nullable = false)
    private boolean committed = false;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** The target text that will be migrated when this candidate is committed. */
    public String effectiveTarget() {
        return finalTarget != null ? finalTarget : proposedTarget;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getConflictGroupId() { return conflictGroupId; }
    public void setConflictGroupId(Long conflictGroupId) { this.conflictGroupId = conflictGroupId; }
    public String getSourceLang() { return sourceLang; }
    public void setSourceLang(String sourceLang) { this.sourceLang = sourceLang; }
    public String getTargetLang() { return targetLang; }
    public void setTargetLang(String targetLang) { this.targetLang = targetLang; }
    public String getProductLine() { return productLine; }
    public void setProductLine(String productLine) { this.productLine = productLine; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public String getProposedTarget() { return proposedTarget; }
    public void setProposedTarget(String proposedTarget) { this.proposedTarget = proposedTarget; }
    public String getFinalTarget() { return finalTarget; }
    public void setFinalTarget(String finalTarget) { this.finalTarget = finalTarget; }
    public String getIdentityKey() { return identityKey; }
    public void setIdentityKey(String identityKey) { this.identityKey = identityKey; }
    public String getGroupKey() { return groupKey; }
    public void setGroupKey(String groupKey) { this.groupKey = groupKey; }
    public String getProposalKey() { return proposalKey; }
    public void setProposalKey(String proposalKey) { this.proposalKey = proposalKey; }
    public CandidateStatus getStatus() { return status; }
    public void setStatus(CandidateStatus status) { this.status = status; }
    public boolean isCommitted() { return committed; }
    public void setCommitted(boolean committed) { this.committed = committed; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
