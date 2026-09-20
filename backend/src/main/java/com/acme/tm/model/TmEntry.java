package com.acme.tm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tm_entry")
public class TmEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

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

    @Column(name = "origin_batch_id")
    private Long originBatchId;

    @Column(name = "origin_candidate_id")
    private Long originCandidateId;

    protected TmEntry() {}

    public TmEntry(Long versionId, String sourceLang, String targetLang, String productLine,
                   String sourceText, String targetText, Long originBatchId, Long originCandidateId) {
        this.versionId = versionId;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.productLine = productLine;
        this.sourceText = sourceText;
        this.targetText = targetText;
        this.originBatchId = originBatchId;
        this.originCandidateId = originCandidateId;
    }

    /** Copy constructor used when a new version inherits entries from its parent. */
    public TmEntry copyFor(Long newVersionId) {
        return new TmEntry(newVersionId, sourceLang, targetLang, productLine,
                sourceText, targetText, originBatchId, originCandidateId);
    }

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public String getProductLine() { return productLine; }
    public String getSourceText() { return sourceText; }
    public String getTargetText() { return targetText; }
    public void setTargetText(String targetText) { this.targetText = targetText; }
    public Long getOriginBatchId() { return originBatchId; }
    public Long getOriginCandidateId() { return originCandidateId; }
}
