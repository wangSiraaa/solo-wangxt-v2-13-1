package com.tmhub.api;

import com.tmhub.domain.Batch;
import com.tmhub.service.BatchService;
import com.tmhub.service.Hashes;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;
    private final com.tmhub.repo.BatchRepository batchRepository;

    public BatchController(BatchService batchService, com.tmhub.repo.BatchRepository batchRepository) {
        this.batchService = batchService;
        this.batchRepository = batchRepository;
    }

    public record InitiateRequest(long sourceVersionId, String sourceLang, String targetLang,
                                  String productLine, String vendor, String tmxFingerprint,
                                  Long termMappingRuleId, String mappingRulesSnapshot,
                                  Integer expectedShards) {}

    /** Idempotent: same delivery (file fingerprint + scope + vendor) always returns the same batch. */
    @PostMapping
    public Batch initiate(@RequestBody InitiateRequest req) {
        return batchService.initiate(new BatchService.InitiateCommand(
                req.sourceVersionId(), req.sourceLang(), req.targetLang(), req.productLine(),
                req.vendor(), req.tmxFingerprint(), req.termMappingRuleId(),
                req.mappingRulesSnapshot(),
                req.expectedShards() != null ? req.expectedShards() : 1));
    }

    /** Shards may arrive out of order and may be re-uploaded; both are safe. */
    @PostMapping("/{id}/shards/{index}")
    public Batch uploadShard(@PathVariable long id, @PathVariable int index,
                             @RequestBody byte[] content) {
        return batchService.uploadShard(id, index, content);
    }

    @GetMapping
    public List<Batch> list() {
        return batchRepository.findAll();
    }

    @GetMapping("/{id}")
    public Batch get(@PathVariable long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new com.tmhub.service.NotFoundException("batch " + id));
    }

    /** Convenience for clients: fingerprint of a file they are about to deliver. */
    @PostMapping("/fingerprint")
    public String fingerprint(@RequestBody byte[] content) {
        return Hashes.sha256(content);
    }
}
