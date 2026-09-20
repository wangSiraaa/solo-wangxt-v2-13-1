package com.acme.tm.repo;

import com.acme.tm.model.TermMappingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermMappingRuleRepo extends JpaRepository<TermMappingRule, Long> {
    List<TermMappingRule> findBySourceLangAndTargetLangAndProductLine(String sourceLang, String targetLang, String productLine);
}
