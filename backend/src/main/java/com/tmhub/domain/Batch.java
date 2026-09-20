package com.tmhub.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "batch")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "source_version_id", nullable = false)
    private Long sourceVersionId;

    @Column(name = "source_lang", nullable = false, length = 10)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 10)
    private String targetLang;

    @Column(name = "product_line", nullable = false, length = 50)
    private String productLine;

    @Column(nullable = false, length = 100)
    private String vendor;

    @Column(name = "tmx_fingerprint", nullable = false, length = 64)
    private String tmxFingerprint;

    @Column(name = "term_mapping_rule_id")
    private Long termMappingRuleId;

    /** Immutable snapshot of the term mapping rules bound to this batch. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "mapping_rules_snapshot", columnDefinition = "jsonb")
    private String mappingRulesSnapshot;

    @Column(name = "expected_shards", nullable = false)
    private int expectedShards = 1;

    @Column(name = "received_shards", nullable = false)
    private int receivedShards = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.RECEIVING;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public void setSourceVersionId(Long sourceVersionId) { this.sourceVersionId = sourceVersionId; }
    public String getSourceLang() { return sourceLang; }
    public void setSourceLang(String sourceLang) { this.sourceLang = sourceLang; }
    public String getTargetLang() { return targetLang; }
    public void setTargetLang(String targetLang) { this.targetLang = targetLang; }
    public String getProductLine() { return productLine; }
    public void setProductLine(String productLine) { this.productLine = productLine; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getTmxFingerprint() { return tmxFingerprint; }
    public void setTmxFingerprint(String tmxFingerprint) { this.tmxFingerprint = tmxFingerprint; }
    public Long getTermMappingRuleId() { return termMappingRuleId; }
    public void setTermMappingRuleId(Long termMappingRuleId) { this.termMappingRuleId = termMappingRuleId; }
    public String getMappingRulesSnapshot() { return mappingRulesSnapshot; }
    public void setMappingRulesSnapshot(String mappingRulesSnapshot) { this.mappingRulesSnapshot = mappingRulesSnapshot; }
    public int getExpectedShards() { return expectedShards; }
    public void setExpectedShards(int expectedShards) { this.expectedShards = expectedShards; }
    public int getReceivedShards() { return receivedShards; }
    public void setReceivedShards(int receivedShards) { this.receivedShards = receivedShards; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
