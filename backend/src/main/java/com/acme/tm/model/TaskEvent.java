package com.acme.tm.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_event")
public class TaskEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TaskEvent() {}

    public TaskEvent(Long taskId, String fromStatus, String toStatus, String message) {
        this.taskId = taskId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.message = message;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
