package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, Long> {

    List<ImportBatchEntity> findTop20ByOrderByCreatedAtDesc();
    List<ImportBatchEntity> findTop20ByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<ImportBatchEntity> findByIdAndCompanyId(Long batchId, Long companyId);

    @EntityGraph(attributePaths = "runs")
    Optional<ImportBatchEntity> findWithRunsByIdAndCompanyId(Long batchId, Long companyId);
}
