package com.tmhub.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "version_pointer_history")
public class VersionPointerHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_version")
    private Long fromVersion;

    @Column(name = "to_version", nullable = false)
    private Long toVersion;

    @Column(name = "moved_at", nullable = false)
    private OffsetDateTime movedAt = OffsetDateTime.now();

    @Column(name = "moved_by", length = 100)
    private String movedBy;

    @Column(columnDefinition = "text")
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromVersion() { return fromVersion; }
    public void setFromVersion(Long fromVersion) { this.fromVersion = fromVersion; }
    public Long getToVersion() { return toVersion; }
    public void setToVersion(Long toVersion) { this.toVersion = toVersion; }
    public OffsetDateTime getMovedAt() { return movedAt; }
    public void setMovedAt(OffsetDateTime movedAt) { this.movedAt = movedAt; }
    public String getMovedBy() { return movedBy; }
    public void setMovedBy(String movedBy) { this.movedBy = movedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
