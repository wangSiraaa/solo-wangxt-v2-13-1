package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tm_version")
public class TmVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 200)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus status = VersionStatus.PUBLISHED;

    @Column(nullable = false)
    private boolean effective;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Lob
    @Column(name = "batch_manifest", columnDefinition = "TEXT")
    private String batchManifest;

    @Lob
    @Column(name = "conflict_report", columnDefinition = "TEXT")
    private String conflictReport;

    @Column(name = "rollback_reason", length = 2000)
    private String rollbackReason;

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TmVersion() {}

    public TmVersion(Long parentId, String label, VersionStatus status, String checksum) {
        this.parentId = parentId;
        this.label = label;
        this.status = status;
        this.checksum = checksum;
    }

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getLabel() { return label; }
    public VersionStatus getStatus() { return status; }
    public void setStatus(VersionStatus status) { this.status = status; }
    public boolean isEffective() { return effective; }
    public void setEffective(boolean effective) { this.effective = effective; }
    public String getChecksum() { return checksum; }
    public String getBatchManifest() { return batchManifest; }
    public void setBatchManifest(String batchManifest) { this.batchManifest = batchManifest; }
    public String getConflictReport() { return conflictReport; }
    public void setConflictReport(String conflictReport) { this.conflictReport = conflictReport; }
    public String getRollbackReason() { return rollbackReason; }
    public void setRollbackReason(String rollbackReason) { this.rollbackReason = rollbackReason; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
