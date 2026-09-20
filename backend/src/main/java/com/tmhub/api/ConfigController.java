package com.tmhub.api;

import com.tmhub.domain.LanguageCaseRule;
import com.tmhub.domain.TermMappingRule;
import com.tmhub.repo.LanguageCaseRuleRepository;
import com.tmhub.repo.TermMappingRuleRepository;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final TermMappingRuleRepository termMappingRuleRepository;
    private final LanguageCaseRuleRepository caseRuleRepository;

    public ConfigController(TermMappingRuleRepository termMappingRuleRepository,
                            LanguageCaseRuleRepository caseRuleRepository) {
        this.termMappingRuleRepository = termMappingRuleRepository;
        this.caseRuleRepository = caseRuleRepository;
    }

    public record MappingRuleRequest(String name, String vendor, String rules) {}

    @PostMapping("/term-mapping-rules")
    public TermMappingRule createMappingRule(@RequestBody MappingRuleRequest req) {
        TermMappingRule rule = new TermMappingRule();
        rule.setName(req.name());
        rule.setVendor(req.vendor());
        rule.setRules(req.rules());
        return termMappingRuleRepository.save(rule);
    }

    @GetMapping("/term-mapping-rules")
    public List<TermMappingRule> mappingRules() {
        return termMappingRuleRepository.findAll();
    }

    public record CaseRuleRequest(String language, String term, String requiredForm,
                                  Boolean sentenceStartUpper) {}

    @PostMapping("/case-rules")
    public LanguageCaseRule createCaseRule(@RequestBody CaseRuleRequest req) {
        LanguageCaseRule rule = new LanguageCaseRule();
        rule.setLanguage(req.language());
        rule.setTerm(req.term());
        rule.setRequiredForm(req.requiredForm());
        rule.setSentenceStartUpper(Boolean.TRUE.equals(req.sentenceStartUpper()));
        return caseRuleRepository.save(rule);
    }

    @GetMapping("/case-rules")
    public List<LanguageCaseRule> caseRules() {
        return caseRuleRepository.findAll();
    }
}
