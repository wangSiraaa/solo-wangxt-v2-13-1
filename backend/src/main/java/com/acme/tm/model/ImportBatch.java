package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "import_batch")
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 600)
    private String idempotencyKey;

    @Column(name = "source_version_id")
    private Long sourceVersionId;

    @Column(name = "source_lang", nullable = false, length = 20)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 20)
    private String targetLang;

    @Column(name = "product_line", nullable = false, length = 100)
    private String productLine;

    @Column(nullable = false, length = 100)
    private String vendor;

    @Column(name = "tmx_fingerprint", nullable = false, length = 64)
    private String tmxFingerprint;

    @Lob
    @Column(name = "mapping_rules", columnDefinition = "TEXT")
    private String mappingRules;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.RECEIVED;

    @Column(name = "total_candidates", nullable = false)
    private int totalCandidates;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ImportBatch() {}

    public ImportBatch(String idempotencyKey, Long sourceVersionId, String sourceLang, String targetLang,
                       String productLine, String vendor, String tmxFingerprint, String mappingRules) {
        this.idempotencyKey = idempotencyKey;
        this.sourceVersionId = sourceVersionId;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.productLine = productLine;
        this.vendor = vendor;
        this.tmxFingerprint = tmxFingerprint;
        this.mappingRules = mappingRules;
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public String getProductLine() { return productLine; }
    public String getVendor() { return vendor; }
    public String getTmxFingerprint() { return tmxFingerprint; }
    public String getMappingRules() { return mappingRules; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public int getTotalCandidates() { return totalCandidates; }
    public void setTotalCandidates(int totalCandidates) { this.totalCandidates = totalCandidates; }
    public Instant getCreatedAt() { return createdAt; }
}
