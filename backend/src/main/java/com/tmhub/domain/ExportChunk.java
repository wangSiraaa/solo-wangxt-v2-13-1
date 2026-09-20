package com.tmhub.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "export_chunk")
public class ExportChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "export_task_id", nullable = false)
    private Long exportTaskId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExportTaskId() { return exportTaskId; }
    public void setExportTaskId(Long exportTaskId) { this.exportTaskId = exportTaskId; }
    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
