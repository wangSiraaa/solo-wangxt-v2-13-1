package com.tmhub.api;

import com.tmhub.domain.MigrationCommit;
import com.tmhub.domain.MigrationTask;
import com.tmhub.service.MigrationEngine;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final MigrationEngine engine;

    public TaskController(MigrationEngine engine) {
        this.engine = engine;
    }

    public record CreateTaskRequest(long batchId, String name) {}

    @PostMapping
    public MigrationTask create(@RequestBody CreateTaskRequest req) {
        return engine.createTask(req.batchId(), req.name());
    }

    @PostMapping("/{id}/start")
    public MigrationTask start(@PathVariable long id) {
        engine.start(id);
        return engine.run(id);
    }

    @PostMapping("/{id}/pause")
    public MigrationTask pause(@PathVariable long id) {
        return engine.pause(id);
    }

    @PostMapping("/{id}/resume")
    public MigrationTask resume(@PathVariable long id) {
        return engine.resume(id);
    }

    @GetMapping
    public List<MigrationTask> list() {
        return engine.list();
    }

    /** Task state incl. checkpoint (lastCandidateId) and committed count. */
    @GetMapping("/{id}")
    public MigrationTask get(@PathVariable long id) {
        return engine.get(id);
    }

    @GetMapping("/{id}/commits")
    public List<MigrationCommit> commits(@PathVariable long id) {
        return engine.commitsOf(id);
    }
}
