package com.acme.tm.repo;

import com.acme.tm.model.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadSessionRepo extends JpaRepository<UploadSession, Long> {
    Optional<UploadSession> findByClientKey(String clientKey);
}
