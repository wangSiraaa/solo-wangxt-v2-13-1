package com.acme.tm.controller;

import com.acme.tm.dto.Dtos.*;
import com.acme.tm.model.ImportBatch;
import com.acme.tm.service.BatchService;
import com.acme.tm.repo.ImportBatchRepo;
import com.acme.tm.error.ApiException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
public class BatchController {
    private final BatchService batchService;
    private final ImportBatchRepo batchRepo;

    public BatchController(BatchService batchService, ImportBatchRepo batchRepo) {
        this.batchService = batchService;
        this.batchRepo = batchRepo;
    }

    /** Single-shot import (idempotent by vendor+langs+productLine+fingerprint). */
    @PostMapping
    public BatchView importBatch(@Valid @RequestBody ImportRequest req) {
        ImportBatch b = batchService.importTmx(req.vendor(), req.sourceLang(), req.targetLang(),
                req.productLine(), req.sourceVersionId(), req.tmx());
        return BatchView.of(b);
    }

    @PostMapping("/uploads")
    public Object initUpload(@Valid @RequestBody InitUploadRequest req) {
        var s = batchService.initSession(req.clientKey(), req.vendor(), req.sourceLang(), req.targetLang(),
                req.productLine(), req.totalChunks(), req.fingerprint());
        return java.util.Map.of("clientKey", s.getClientKey(), "status", s.getStatus(),
                "batchId", s.getBatchId() == null ? -1 : s.getBatchId());
    }

    /** Shards may arrive out of order; duplicates are absorbed. */
    @PostMapping("/uploads/{clientKey}/chunks/{index}")
    public Object addChunk(@PathVariable String clientKey, @PathVariable int index,
                           @Valid @RequestBody ChunkRequest req) {
        batchService.addChunk(clientKey, index, req.data());
        return java.util.Map.of("clientKey", clientKey, "index", index, "stored", true);
    }

    @PostMapping("/uploads/{clientKey}/complete")
    public BatchView complete(@PathVariable String clientKey, @RequestBody(required = false) CompleteUploadRequest req) {
        return BatchView.of(batchService.completeSession(clientKey, req == null ? null : req.sourceVersionId()));
    }

    @GetMapping
    public List<BatchView> list() {
        return batchRepo.findAll().stream().map(BatchView::of).toList();
    }

    @GetMapping("/{id}")
    public BatchView get(@PathVariable Long id) {
        return batchRepo.findById(id).map(BatchView::of)
                .orElseThrow(() -> ApiException.notFound("batch " + id));
    }

    @GetMapping("/{id}/candidates")
    public List<CandidateView> candidates(@PathVariable Long id,
                                          @RequestParam(required = false) String status) {
        var all = batchService.candidates(id);
        if (status == null || status.isBlank()) return all.stream().map(CandidateView::of).toList();
        return all.stream().filter(c -> c.getStatus().name().equalsIgnoreCase(status))
                .map(CandidateView::of).toList();
    }

    /** Conflict chain: every candidate sharing the conflict group, across vendors/batches. */
    @GetMapping("/conflicts/{groupId}")
    public List<CandidateView> conflictChain(@PathVariable Long groupId) {
        return batchService.conflictChain(groupId).stream().map(CandidateView::of).toList();
    }
}
