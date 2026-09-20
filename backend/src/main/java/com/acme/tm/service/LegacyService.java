package com.acme.tm.service;

import com.acme.tm.error.ApiException;
import com.acme.tm.model.*;
import com.acme.tm.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pre-existing single-vendor flows, kept intact: direct TMX import that publishes a
 * version immediately, plain term replacement, and downloads of any historical version.
 */
@Service
public class LegacyService {
    private final TmVersionRepo versionRepo;
    private final TmEntryRepo entryRepo;
    private final TermMappingRuleRepo ruleRepo;
    private final LanguageProfileRepo profileRepo;
    private final PublishService publishService;

    public LegacyService(TmVersionRepo versionRepo, TmEntryRepo entryRepo, TermMappingRuleRepo ruleRepo,
                         LanguageProfileRepo profileRepo, PublishService publishService) {
        this.versionRepo = versionRepo;
        this.entryRepo = entryRepo;
        this.ruleRepo = ruleRepo;
        this.profileRepo = profileRepo;
        this.publishService = publishService;
    }

    /** Legacy path: single-vendor TMX import creates an effective version directly (no batch). */
    @Transactional
    public TmVersion importLegacy(String vendor, String sourceLang, String targetLang,
                                  String productLine, String label, String tmxContent) {
        TmVersion parent = versionRepo.findByEffectiveTrue().orElse(null);
        List<TmxParser.Tu> tus = TmxParser.parse(tmxContent);
        List<TmEntry> entries = new java.util.ArrayList<>();
        for (TmxParser.Tu tu : tus) {
            String src = tu.segmentsByLang().get(sourceLang);
            String tgt = tu.segmentsByLang().get(targetLang);
            if (src == null || tgt == null) continue;
            entries.add(new TmEntry(0L, sourceLang, targetLang, productLine, src, tgt, null, null));
        }
        TmVersion v = new TmVersion(parent == null ? null : parent.getId(),
                label + " (vendor " + vendor + ")", VersionStatus.PUBLISHED, publishService.checksumOf(entries));
        v.setEffective(true);
        v = versionRepo.save(v);
        v.setDownloadUrl("/api/versions/" + v.getId() + "/tmx");
        v = versionRepo.save(v);
        for (TmEntry e : entries) {
            entryRepo.save(new TmEntry(v.getId(), e.getSourceLang(), e.getTargetLang(), e.getProductLine(),
                    e.getSourceText(), e.getTargetText(), null, null));
        }
        if (parent != null) {
            parent.setEffective(false);
            if (parent.getStatus() == VersionStatus.PUBLISHED) parent.setStatus(VersionStatus.SUPERSEDED);
            versionRepo.save(parent);
        }
        return v;
    }

    /** Legacy plain term replacement; polysemous terms refuse to guess and ask for a choice. */
    public String replaceTerms(String text, String sourceLang, String targetLang, String productLine) {
        List<TermMappingRule> rules = ruleRepo.findBySourceLangAndTargetLangAndProductLine(
                sourceLang, targetLang, productLine);
        CaseRule caseRule = profileRepo.findByCode(targetLang)
                .map(LanguageProfile::getCaseRule).orElse(CaseRule.PRESERVE);
        String out = text;
        for (TermMappingRule r : rules) {
            if (r.isPolysemous() && out.contains(r.getSourceTerm())) {
                throw ApiException.unprocessable("polysemous term '" + r.getSourceTerm()
                        + "' requires a human choice among: " + r.getAlternatives());
            }
            String replacement = caseRule.apply(r.getApprovedTarget());
            if (r.getReplacedTarget() != null && !r.getReplacedTarget().isBlank()) {
                out = out.replaceAll("(?i)" + Pattern.quote(r.getReplacedTarget()),
                        Matcher.quoteReplacement(replacement));
            }
        }
        return out;
    }
}
