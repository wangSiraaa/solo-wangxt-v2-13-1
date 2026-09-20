package com.acme.tm.repo;

import com.acme.tm.model.LanguageProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LanguageProfileRepo extends JpaRepository<LanguageProfile, Long> {
    Optional<LanguageProfile> findByCode(String code);
}
