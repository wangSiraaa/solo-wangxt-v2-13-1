package com.acme.tm.controller;

import com.acme.tm.dto.Dtos.*;
import com.acme.tm.service.LegacyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** The pre-existing single-vendor flows, unchanged by the batch mechanism. */
@RestController
@RequestMapping("/api/legacy")
public class LegacyController {
    private final LegacyService legacyService;

    public LegacyController(LegacyService legacyService) {
        this.legacyService = legacyService;
    }

    @PostMapping("/import")
    public VersionView importLegacy(@Valid @RequestBody LegacyImportRequest req) {
        return VersionView.of(legacyService.importLegacy(req.vendor(), req.sourceLang(), req.targetLang(),
                req.productLine(), req.label(), req.tmx()));
    }

    @PostMapping("/replace")
    public ReplaceResponse replace(@Valid @RequestBody ReplaceRequest req) {
        return new ReplaceResponse(legacyService.replaceTerms(req.text(), req.sourceLang(),
                req.targetLang(), req.productLine()));
    }
}
