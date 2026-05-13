package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.ReconciliationSessionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationSessionRepository extends JpaRepository<ReconciliationSessionEntity, Long> {
    List<ReconciliationSessionEntity> findTop20ByOrderByCreatedAtDesc();
    List<ReconciliationSessionEntity> findTop20ByCompanyIdOrderByCreatedAtDesc(Long companyId);
    java.util.Optional<ReconciliationSessionEntity> findByIdAndCompanyId(Long id, Long companyId);
}
