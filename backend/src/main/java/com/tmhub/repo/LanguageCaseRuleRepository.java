package com.tmhub.repo;

import com.tmhub.domain.LanguageCaseRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LanguageCaseRuleRepository extends JpaRepository<LanguageCaseRule, Long> {
    List<LanguageCaseRule> findByLanguage(String language);
}
