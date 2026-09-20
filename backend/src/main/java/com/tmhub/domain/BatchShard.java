package com.tmhub.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "batch_shard")
@IdClass(BatchShard.BatchShardId.class)
public class BatchShard {

    public static class BatchShardId implements Serializable {
        private Long batchId;
        private Integer shardIndex;

        public BatchShardId() {}
        public BatchShardId(Long batchId, Integer shardIndex) {
            this.batchId = batchId;
            this.shardIndex = shardIndex;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BatchShardId that)) return false;
            return Objects.equals(batchId, that.batchId) && Objects.equals(shardIndex, that.shardIndex);
        }
        @Override
        public int hashCode() { return Objects.hash(batchId, shardIndex); }
    }

    @Id
    @Column(name = "batch_id")
    private Long batchId;

    @Id
    @Column(name = "shard_index")
    private Integer shardIndex;

    @Column(name = "shard_fingerprint", nullable = false, length = 64)
    private String shardFingerprint;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getShardIndex() { return shardIndex; }
    public void setShardIndex(Integer shardIndex) { this.shardIndex = shardIndex; }
    public String getShardFingerprint() { return shardFingerprint; }
    public void setShardFingerprint(String shardFingerprint) { this.shardFingerprint = shardFingerprint; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(OffsetDateTime receivedAt) { this.receivedAt = receivedAt; }
}
