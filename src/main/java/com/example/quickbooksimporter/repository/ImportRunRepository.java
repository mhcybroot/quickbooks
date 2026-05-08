package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRunEntity, Long> {

    List<ImportRunEntity> findTop20ByOrderByCreatedAtDesc();

    List<ImportRunEntity> findTop100ByOrderByCreatedAtDesc();

    List<ImportRunEntity> findByBatchIdOrderByBatchOrderAscCreatedAtAsc(Long batchId);

    Optional<ImportRunEntity> findTopByEntityTypeAndMappingProfileNameIsNotNullOrderByCreatedAtDesc(EntityType entityType);
}
