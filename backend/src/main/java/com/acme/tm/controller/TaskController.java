package com.acme.tm.controller;

import com.acme.tm.dto.Dtos.*;
import com.acme.tm.model.MigrationTask;
import com.acme.tm.service.MigrationTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class TaskController {
    private final MigrationTaskService taskService;

    public TaskController(MigrationTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/batches/{batchId}/tasks")
    public TaskView create(@PathVariable Long batchId) {
        return TaskView.of(taskService.create(batchId));
    }

    @PostMapping("/api/tasks/{id}/run")
    public TaskView run(@PathVariable Long id, @RequestBody(required = false) RunRequest req) {
        int chunkSize = req != null && req.chunkSize() != null ? req.chunkSize() : 50;
        int maxChunks = req != null && req.maxChunks() != null ? req.maxChunks() : 1000;
        return TaskView.of(taskService.run(id, chunkSize, maxChunks, null));
    }

    @PostMapping("/api/tasks/{id}/pause")
    public TaskView pause(@PathVariable Long id) {
        return TaskView.of(taskService.pause(id));
    }

    @PostMapping("/api/tasks/{id}/resume")
    public TaskView resume(@PathVariable Long id) {
        return TaskView.of(taskService.resume(id));
    }

    /** Task state, checkpoint and full transition history for traceability. */
    @GetMapping("/api/tasks/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        MigrationTask t = taskService.mustLoad(id);
        return Map.of(
                "task", TaskView.of(t),
                "events", taskService.events(id).stream().map(TaskEventView::of).toList(),
                "commits", taskService.commits(id).size());
    }
}
