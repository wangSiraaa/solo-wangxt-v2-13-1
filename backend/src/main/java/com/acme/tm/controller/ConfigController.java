package com.acme.tm.controller;

import com.acme.tm.dto.Dtos.*;
import com.acme.tm.model.LanguageProfile;
import com.acme.tm.model.TermMappingRule;
import com.acme.tm.repo.LanguageProfileRepo;
import com.acme.tm.repo.TermMappingRuleRepo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final LanguageProfileRepo profileRepo;
    private final TermMappingRuleRepo ruleRepo;

    public ConfigController(LanguageProfileRepo profileRepo, TermMappingRuleRepo ruleRepo) {
        this.profileRepo = profileRepo;
        this.ruleRepo = ruleRepo;
    }

    @PostMapping("/language-profiles")
    public LanguageProfile upsertProfile(@Valid @RequestBody ProfileRequest req) {
        LanguageProfile p = profileRepo.findByCode(req.code())
                .orElseGet(() -> new LanguageProfile(req.code(), req.caseRule()));
        p.setCaseRule(req.caseRule());
        return profileRepo.save(p);
    }

    @GetMapping("/language-profiles")
    public List<LanguageProfile> profiles() {
        return profileRepo.findAll();
    }

    @PostMapping("/term-rules")
    public TermMappingRule addRule(@Valid @RequestBody RuleRequest req) {
        return ruleRepo.save(new TermMappingRule(req.sourceLang(), req.targetLang(), req.productLine(),
                req.sourceTerm(), req.approvedTarget(), req.replacedTarget(), req.polysemous(),
                req.alternatives(), req.note()));
    }

    @GetMapping("/term-rules")
    public List<TermMappingRule> rules() {
        return ruleRepo.findAll();
    }
}
