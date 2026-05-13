package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRunEntity, Long> {

    List<ImportRunEntity> findTop20ByOrderByCreatedAtDesc();

    List<ImportRunEntity> findTop100ByOrderByCreatedAtDesc();
    List<ImportRunEntity> findTop100ByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<ImportRunEntity> findTop20ByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<ImportRunEntity> findByBatchIdOrderByBatchOrderAscCreatedAtAsc(Long batchId);
    List<ImportRunEntity> findByBatchIdAndCompanyIdOrderByBatchOrderAscCreatedAtAsc(Long batchId, Long companyId);

    Optional<ImportRunEntity> findTopByEntityTypeAndMappingProfileNameIsNotNullOrderByCreatedAtDesc(EntityType entityType);
    Optional<ImportRunEntity> findTopByEntityTypeAndMappingProfileNameIsNotNullAndCompanyIdOrderByCreatedAtDesc(EntityType entityType, Long companyId);
    Optional<ImportRunEntity> findTopByEntityTypeAndSourceFileNameAndCompanyIdOrderByCreatedAtDesc(EntityType entityType, String sourceFileName, Long companyId);

    Optional<ImportRunEntity> findByIdAndCompanyId(Long runId, Long companyId);
}
