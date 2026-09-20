package com.tmhub.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "version_conflict_resolution")
public class VersionConflictResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "conflict_group_id", nullable = false)
    private Long conflictGroupId;

    @Column(name = "resolved_candidate_id")
    private Long resolvedCandidateId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getConflictGroupId() { return conflictGroupId; }
    public void setConflictGroupId(Long conflictGroupId) { this.conflictGroupId = conflictGroupId; }
    public Long getResolvedCandidateId() { return resolvedCandidateId; }
    public void setResolvedCandidateId(Long resolvedCandidateId) { this.resolvedCandidateId = resolvedCandidateId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
