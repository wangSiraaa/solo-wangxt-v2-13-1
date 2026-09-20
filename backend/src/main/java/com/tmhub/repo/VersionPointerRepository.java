package com.tmhub.repo;

import com.tmhub.domain.VersionPointer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VersionPointerRepository extends JpaRepository<VersionPointer, Short> {
}
