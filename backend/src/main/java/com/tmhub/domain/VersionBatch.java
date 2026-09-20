package com.tmhub.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "version_batch")
@IdClass(VersionBatch.VersionBatchId.class)
public class VersionBatch {

    public static class VersionBatchId implements Serializable {
        private Long versionId;
        private Long batchId;

        public VersionBatchId() {}
        public VersionBatchId(Long versionId, Long batchId) {
            this.versionId = versionId;
            this.batchId = batchId;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VersionBatchId that)) return false;
            return Objects.equals(versionId, that.versionId) && Objects.equals(batchId, that.batchId);
        }
        @Override
        public int hashCode() { return Objects.hash(versionId, batchId); }
    }

    @Id
    @Column(name = "version_id")
    private Long versionId;

    @Id
    @Column(name = "batch_id")
    private Long batchId;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
}
