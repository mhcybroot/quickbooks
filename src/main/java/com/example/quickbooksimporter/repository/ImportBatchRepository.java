package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatchEntity, Long> {

    List<ImportBatchEntity> findTop20ByOrderByCreatedAtDesc();
    List<ImportBatchEntity> findTop20ByCompanyIdOrderByCreatedAtDesc(Long companyId);
    java.util.Optional<ImportBatchEntity> findByIdAndCompanyId(Long batchId, Long companyId);
}
