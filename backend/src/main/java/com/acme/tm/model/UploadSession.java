package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "upload_session")
public class UploadSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_key", nullable = false, unique = true, length = 200)
    private String clientKey;

    @Column(nullable = false, length = 100)
    private String vendor;

    @Column(name = "source_lang", nullable = false, length = 20)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 20)
    private String targetLang;

    @Column(name = "product_line", nullable = false, length = 100)
    private String productLine;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false, length = 20)
    private String status = "OPEN"; // OPEN | COMPLETED

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UploadSession() {}

    public UploadSession(String clientKey, String vendor, String sourceLang, String targetLang,
                         String productLine, int totalChunks, String fingerprint) {
        this.clientKey = clientKey;
        this.vendor = vendor;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.productLine = productLine;
        this.totalChunks = totalChunks;
        this.fingerprint = fingerprint;
    }

    public Long getId() { return id; }
    public String getClientKey() { return clientKey; }
    public String getVendor() { return vendor; }
    public String getSourceLang() { return sourceLang; }
    public String getTargetLang() { return targetLang; }
    public String getProductLine() { return productLine; }
    public int getTotalChunks() { return totalChunks; }
    public String getFingerprint() { return fingerprint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Instant getCreatedAt() { return createdAt; }
}
