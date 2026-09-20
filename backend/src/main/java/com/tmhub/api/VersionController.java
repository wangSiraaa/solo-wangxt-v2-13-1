package com.tmhub.api;

import com.tmhub.domain.ExportArtifact;
import com.tmhub.domain.ExportTask;
import com.tmhub.domain.TmVersion;
import com.tmhub.service.ExportService;
import com.tmhub.service.PublishService;
import com.tmhub.service.VersionQueryService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/versions")
public class VersionController {

    private final PublishService publishService;
    private final VersionQueryService versionQueryService;
    private final ExportService exportService;

    public VersionController(PublishService publishService, VersionQueryService versionQueryService,
                             ExportService exportService) {
        this.publishService = publishService;
        this.versionQueryService = versionQueryService;
        this.exportService = exportService;
    }

    public record PublishRequest(String label, List<Long> batchIds, String actor) {}

    /** Release: new immutable version with parent, batch manifest, conflict records, checksum. */
    @PostMapping("/publish")
    public TmVersion publish(@RequestBody PublishRequest req) {
        return publishService.publish(req.label(), req.batchIds(),
                req.actor() != null ? req.actor() : "system");
    }

    public record RollbackRequest(String mode, String reason, String actor) {}

    /** Rollback only ever creates a reverse version or moves the pointer; history is immutable. */
    @PostMapping("/{id}/rollback")
    public TmVersion rollback(@PathVariable long id, @RequestBody RollbackRequest req) {
        PublishService.RollbackMode mode = req.mode() != null
                ? PublishService.RollbackMode.valueOf(req.mode())
                : PublishService.RollbackMode.REVERSE_VERSION;
        return publishService.rollback(id, mode, req.reason(),
                req.actor() != null ? req.actor() : "system");
    }

    @PostMapping("/baseline")
    public TmVersion baseline(@RequestParam String label, @RequestParam(required = false) String actor) {
        return publishService.createBaseline(label, actor != null ? actor : "system");
    }

    @GetMapping
    public List<TmVersion> list() {
        return versionQueryService.allVersions();
    }

    @GetMapping("/{id}/lineage")
    public VersionQueryService.Lineage lineage(@PathVariable long id) {
        return versionQueryService.lineage(id);
    }

    @PostMapping("/{id}/export")
    public ExportTask startExport(@PathVariable long id) {
        ExportTask task = exportService.startExport(id);
        return exportService.run(task.getId());
    }

    @GetMapping("/{id}/exports")
    public List<ExportTask> exportTasks(@PathVariable long id) {
        return exportService.tasksOf(id);
    }

    /**
     * Download the TMX of any version. Old versions keep working download URLs because
     * published versions and their artifacts are never deleted or rewritten.
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<String> download(@PathVariable long id) {
        ExportArtifact artifact = exportService.latestArtifact(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tm-version-" + id + ".tmx\"")
                .header("X-Checksum", artifact.getChecksum())
                .contentType(MediaType.APPLICATION_XML)
                .body(artifact.getContent());
    }
}
