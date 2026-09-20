package com.tmhub.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "version_pointer")
public class VersionPointer {

    @Id
    private Short id = (short) 1;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "moved_at", nullable = false)
    private OffsetDateTime movedAt = OffsetDateTime.now();

    @Column(name = "moved_by", length = 100)
    private String movedBy;

    @Column(columnDefinition = "text")
    private String note;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public OffsetDateTime getMovedAt() { return movedAt; }
    public void setMovedAt(OffsetDateTime movedAt) { this.movedAt = movedAt; }
    public String getMovedBy() { return movedBy; }
    public void setMovedBy(String movedBy) { this.movedBy = movedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
