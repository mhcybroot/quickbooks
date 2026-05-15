package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.domain.AppJobType;
import com.example.quickbooksimporter.persistence.AppJobEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppJobRepository extends JpaRepository<AppJobEntity, Long> {

    Optional<AppJobEntity> findByIdAndCompanyId(Long id, Long companyId);

    List<AppJobEntity> findTop20ByCompanyIdAndTypeOrderByCreatedAtDesc(Long companyId, AppJobType type);
}
