package com.acme.tm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "upload_chunk")
public class UploadChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String data;

    protected UploadChunk() {}

    public UploadChunk(Long sessionId, int chunkIndex, String data) {
        this.sessionId = sessionId;
        this.chunkIndex = chunkIndex;
        this.data = data;
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getData() { return data; }
}
