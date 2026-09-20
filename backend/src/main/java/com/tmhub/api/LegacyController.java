package com.tmhub.api;

import com.tmhub.domain.TmVersion;
import com.tmhub.service.LegacyService;
import org.springframework.web.bind.annotation.*;

/** The original pre-batch endpoints, unchanged in behaviour. */
@RestController
@RequestMapping("/api/legacy")
public class LegacyController {

    private final LegacyService legacyService;

    public LegacyController(LegacyService legacyService) {
        this.legacyService = legacyService;
    }

    /** Original single-vendor TMX import. */
    @PostMapping("/import")
    public TmVersion importTmx(@RequestBody byte[] tmxContent,
                               @RequestParam String productLine,
                               @RequestParam String label,
                               @RequestParam(required = false) String actor) {
        return legacyService.importTmx(tmxContent, productLine, label,
                actor != null ? actor : "legacy");
    }

    public record TermReplaceRequest(long versionId, String sourceTerm, String targetTerm, String actor) {}

    /** Original plain term replacement. */
    @PostMapping("/term-replace")
    public TmVersion replaceTerm(@RequestBody TermReplaceRequest req) {
        return legacyService.replaceTerm(req.versionId(), req.sourceTerm(), req.targetTerm(),
                req.actor() != null ? req.actor() : "legacy");
    }
}
