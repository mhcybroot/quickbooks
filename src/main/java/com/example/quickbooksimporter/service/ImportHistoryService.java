package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportBatchRepository;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ImportHistoryService {

    private final ImportRunRepository importRunRepository;
    private final ImportBatchRepository importBatchRepository;

    public ImportHistoryService(ImportRunRepository importRunRepository,
                                ImportBatchRepository importBatchRepository) {
        this.importRunRepository = importRunRepository;
        this.importBatchRepository = importBatchRepository;
    }

    public List<ImportRunEntity> recentRuns() {
        return importRunRepository.findTop100ByOrderByCreatedAtDesc();
    }

    public List<ImportBatchEntity> recentBatches() {
        return importBatchRepository.findTop20ByOrderByCreatedAtDesc();
    }

    public List<ImportRunEntity> filterRuns(EntityType entityType,
                                            ImportRunStatus status,
                                            LocalDate createdOnOrAfter,
                                            String sourceFileSearch) {
        return recentRuns().stream()
                .filter(run -> entityType == null || run.getEntityType() == entityType)
                .filter(run -> status == null || run.getStatus() == status)
                .filter(run -> createdOnOrAfter == null || !run.getCreatedAt().isBefore(createdOnOrAfter.atStartOfDay().toInstant(ZoneOffset.UTC)))
                .filter(run -> sourceFileSearch == null || sourceFileSearch.isBlank()
                        || run.getSourceFileName().toLowerCase().contains(sourceFileSearch.toLowerCase()))
                .toList();
    }
}
