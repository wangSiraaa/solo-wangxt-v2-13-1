package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "batch_candidate")
public class BatchCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "source_lang", nullable = false, length = 20)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 20)
    private String targetLang;

    @Column(name = "product_line", nullable = false, length = 100)
    private String productLine;

    @Lob
    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Lob
    @Column(name = "target_text", nullable = false, columnDefinition = "TEXT")
    private String targetText;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateStatus status = CandidateStatus.PENDING;

    @Column(name = "placeholder_sig", length = 2000)
    private String placeholderSig;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", length = 40)
    private AnomalyType anomalyType;

    @Column(name = "anomaly_detail", length = 2000)
    private String anomalyDetail;

    @Column(name = "conflict_group_id")
    private Long conflictGroupId;

    @Column(name = "term_override", length = 500)
    private String termOverride;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BatchCandidate() {}

    public BatchCandidate(Long batchId, String sourceLang, String targetLang, String productLine,
                          String sourceText, String targetText, String sourceHash, String placeholderSig) {
        this.batchId = batchId;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.productLine = productLine;
        this.sourceText = sourceText;
        this.targetText = targetText;
        this.sourceHash = sourceHash;
        this.placeholderSig = placeholderSig;
    }

    public Long getId() { return id; }
    public Long getBatchId() { return batchId; }
    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public String getProductLine() { return productLine; }
    public String getSourceText() { return sourceText; }
    public String getTargetText() { return targetText; }
    public void setTargetText(String targetText) { this.targetText = targetText; }
    public String getSourceHash() { return sourceHash; }
    public CandidateStatus getStatus() { return status; }
    public void setStatus(CandidateStatus status) { this.status = status; }
    public String getPlaceholderSig() { return placeholderSig; }
    public void setPlaceholderSig(String placeholderSig) { this.placeholderSig = placeholderSig; }
    public AnomalyType getAnomalyType() { return anomalyType; }
    public void setAnomalyType(AnomalyType anomalyType) { this.anomalyType = anomalyType; }
    public String getAnomalyDetail() { return anomalyDetail; }
    public void setAnomalyDetail(String anomalyDetail) { this.anomalyDetail = anomalyDetail; }
    public Long getConflictGroupId() { return conflictGroupId; }
    public void setConflictGroupId(Long conflictGroupId) { this.conflictGroupId = conflictGroupId; }
    public String getTermOverride() { return termOverride; }
    public void setTermOverride(String termOverride) { this.termOverride = termOverride; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
