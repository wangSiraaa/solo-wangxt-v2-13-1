package com.tmhub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmhub.domain.AnomalyType;
import com.tmhub.domain.LanguageCaseRule;
import com.tmhub.repo.LanguageCaseRuleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Validates a proposed replacement before it may be reviewed or migrated.
 *
 * Three hard checks, each producing a quarantining anomaly — never an automatic guess:
 *  1. placeholders/escapes in the target must match the source exactly;
 *  2. language-specific casing rules (per language_case_rule config) must hold;
 *  3. a source term whose mapping rule lists several targets (polysemy) always quarantines.
 */
@Service
public class ValidationService {

    private final LanguageCaseRuleRepository caseRuleRepository;
    private final ObjectMapper objectMapper;

    public ValidationService(LanguageCaseRuleRepository caseRuleRepository, ObjectMapper objectMapper) {
        this.caseRuleRepository = caseRuleRepository;
        this.objectMapper = objectMapper;
    }

    public record Anomaly(AnomalyType type, String detailJson) {}

    public List<Anomaly> validate(String sourceText, String targetText,
                                  String targetLang, String mappingRulesJson) {
        List<Anomaly> anomalies = new ArrayList<>();

        // 1. Placeholder count / name / order and escape forms.
        Placeholders.PlaceholderDiff diff = Placeholders.diff(sourceText, targetText);
        if (!diff.matches()) {
            anomalies.add(new Anomaly(AnomalyType.PLACEHOLDER_MISMATCH, toJson(Map.of(
                    "sourcePlaceholders", diff.sourceTokens(),
                    "targetPlaceholders", diff.targetTokens(),
                    "sourceEscapes", diff.escapeSource(),
                    "targetEscapes", diff.escapeTarget()))));
        }

        // 2. Language casing rules.
        List<LanguageCaseRule> rules = caseRuleRepository.findByLanguage(targetLang);
        for (LanguageCaseRule rule : rules) {
            String required = rule.getRequiredForm();
            if (containsWord(targetText, required)) {
                continue; // exact required casing present
            }
            if (containsWordIgnoreCase(targetText, rule.getTerm())) {
                anomalies.add(new Anomaly(AnomalyType.CASING_VIOLATION, toJson(Map.of(
                        "language", targetLang,
                        "term", rule.getTerm(),
                        "requiredForm", required,
                        "message", "Term '" + rule.getTerm() + "' must appear as '" + required + "'"))));
            }
        }
        boolean sentenceStartUpper = rules.stream().anyMatch(LanguageCaseRule::isSentenceStartUpper);
        if (sentenceStartUpper && !targetText.isBlank()
                && Character.isLetter(targetText.stripLeading().charAt(0))
                && !Character.isUpperCase(targetText.stripLeading().charAt(0))) {
            anomalies.add(new Anomaly(AnomalyType.CASING_VIOLATION, toJson(Map.of(
                    "language", targetLang,
                    "message", "Target must start with an upper-case letter"))));
        }

        // 3. Polysemy from the bound term mapping rules.
        for (Map<String, Object> rule : parseRules(mappingRulesJson)) {
            String sourceTerm = str(rule.get("sourceTerm"));
            @SuppressWarnings("unchecked")
            List<String> targets = (List<String>) rule.getOrDefault("targetTerms", List.of());
            if (sourceTerm == null || targets.size() <= 1) {
                continue;
            }
            if (containsWordIgnoreCase(sourceText, sourceTerm)) {
                anomalies.add(new Anomaly(AnomalyType.POLYSEMY, toJson(Map.of(
                        "sourceTerm", sourceTerm,
                        "candidateTargets", targets,
                        "message", "Polysemous term '" + sourceTerm + "' requires a human choice"))));
            }
        }
        return anomalies;
    }

    public List<Map<String, Object>> parseRules(String rulesJson) {
        if (rulesJson == null || rulesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rulesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BadRequestException("Malformed term mapping rules: " + e.getMessage());
        }
    }

    static boolean containsWord(String text, String term) {
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(term) + "\\b")
                .matcher(text).find();
    }

    static boolean containsWordIgnoreCase(String text, String term) {
        return java.util.regex.Pattern.compile(
                        "\\b" + java.util.regex.Pattern.quote(term) + "\\b",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)
                .matcher(text).find();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
