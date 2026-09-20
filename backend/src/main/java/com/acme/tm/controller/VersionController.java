package com.acme.tm.controller;

import com.acme.tm.dto.Dtos.*;
import com.acme.tm.model.TmVersion;
import com.acme.tm.repo.TmVersionRepo;
import com.acme.tm.error.ApiException;
import com.acme.tm.service.PublishService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/versions")
public class VersionController {
    private final PublishService publishService;
    private final TmVersionRepo versionRepo;

    public VersionController(PublishService publishService, TmVersionRepo versionRepo) {
        this.publishService = publishService;
        this.versionRepo = versionRepo;
    }

    @PostMapping("/publish")
    public VersionView publish(@Valid @RequestBody PublishRequest req) {
        return VersionView.of(publishService.publish(req.label(), req.batchIds()));
    }

    /** Rollback never deletes or rewrites history: it creates a reverse version with a reason. */
    @PostMapping("/{id}/rollback")
    public VersionView rollback(@PathVariable Long id, @Valid @RequestBody RollbackRequest req) {
        return VersionView.of(publishService.rollback(id, req.reason()));
    }

    @GetMapping
    public List<VersionView> list() {
        return versionRepo.findAllByOrderByIdAsc().stream().map(VersionView::of).toList();
    }

    @GetMapping("/{id}")
    public VersionView get(@PathVariable Long id) {
        return versionRepo.findById(id).map(VersionView::of)
                .orElseThrow(() -> ApiException.notFound("version " + id));
    }

    @GetMapping("/{id}/lineage")
    public List<VersionView> lineage(@PathVariable Long id) {
        return publishService.lineage(id).stream().map(VersionView::of).toList();
    }

    /** TMX export. Old versions keep their download URL forever. */
    @GetMapping(value = "/{id}/tmx", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> exportTmx(@PathVariable Long id) {
        TmVersion v = versionRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("version " + id));
        String tmx = publishService.exportTmx(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tm-v" + id + ".tmx\"")
                .header("X-Content-Checksum", v.getChecksum())
                .contentType(MediaType.APPLICATION_XML)
                .body(tmx);
    }

    @GetMapping("/{id}/checksum")
    public Object checksum(@PathVariable Long id) {
        TmVersion v = versionRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("version " + id));
        return java.util.Map.of("versionId", id, "checksum", v.getChecksum());
    }
}
