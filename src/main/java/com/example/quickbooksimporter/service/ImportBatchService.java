package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportBatchStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportBatchRepository;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportBatchService {

    private final ImportBatchRepository importBatchRepository;
    private final ImportRunRepository importRunRepository;
    private final ImportWorkflowFacade workflowFacade;
    private final CurrentCompanyService currentCompanyService;

    public ImportBatchService(ImportBatchRepository importBatchRepository,
                              ImportRunRepository importRunRepository,
                              ImportWorkflowFacade workflowFacade,
                              CurrentCompanyService currentCompanyService) {
        this.importBatchRepository = importBatchRepository;
        this.importRunRepository = importRunRepository;
        this.workflowFacade = workflowFacade;
        this.currentCompanyService = currentCompanyService;
    }

    @Transactional
    public ImportBatchEntity createDraftBatch(String batchName, int totalFiles) {
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setBatchName(batchName);
        batch.setStatus(ImportBatchStatus.DRAFT);
        batch.setTotalFiles(totalFiles);
        batch.setValidatedFiles(0);
        batch.setRunnableFiles(0);
        batch.setCompletedFiles(0);
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        batch.setCompany(currentCompanyService.requireCurrentCompany());
        return importBatchRepository.save(batch);
    }

    @Transactional
    public ImportBatchEntity updateValidationSnapshot(Long batchId, int totalFiles, int validatedFiles, int runnableFiles) {
        ImportBatchEntity batch = importBatchRepository.findByIdAndCompanyId(batchId, currentCompanyService.requireCurrentCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        batch.setTotalFiles(totalFiles);
        batch.setValidatedFiles(validatedFiles);
        batch.setRunnableFiles(runnableFiles);
        batch.setStatus(validatedFiles == 0 ? ImportBatchStatus.DRAFT : ImportBatchStatus.VALIDATED);
        batch.setUpdatedAt(Instant.now());
        return importBatchRepository.save(batch);
    }

    public List<String> dependencyWarnings(List<BatchFileRequest> files) {
        List<ImportRunEntity> recentRuns = importRunRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(currentCompanyService.requireCurrentCompanyId());
        Set<String> knownInvoices = successfulIdentifiers(recentRuns, EntityType.INVOICE);
        Set<String> knownBills = successfulIdentifiers(recentRuns, EntityType.BILL);
        Set<String> batchInvoices = new LinkedHashSet<>();
        Set<String> batchBills = new LinkedHashSet<>();
        for (BatchFileRequest file : files) {
            if (file.previewSummary() == null) {
                continue;
            }
            batchInvoices.addAll(workflowFacade.producedIdentifiers(file.entityType(), file.previewSummary().rawPreview()));
            batchBills.addAll(workflowFacade.producedIdentifiers(file.entityType(), file.previewSummary().rawPreview()));
        }

        List<String> warnings = new ArrayList<>();
        for (BatchFileRequest file : files) {
            if (file.previewSummary() == null) {
                continue;
            }
            if (file.entityType() == EntityType.PAYMENT) {
                Set<String> missing = new LinkedHashSet<>(workflowFacade.requiredParentIdentifiers(file.entityType(), file.previewSummary().rawPreview()));
                missing.removeAll(knownInvoices);
                missing.removeAll(batchInvoices);
                if (!missing.isEmpty()) {
                    warnings.add(file.fileName() + ": invoices not found yet for payments " + String.join(", ", missing));
                }
            }
            if (file.entityType() == EntityType.BILL_PAYMENT) {
                Set<String> missing = new LinkedHashSet<>(workflowFacade.requiredParentIdentifiers(file.entityType(), file.previewSummary().rawPreview()));
                missing.removeAll(knownBills);
                missing.removeAll(batchBills);
                if (!missing.isEmpty()) {
                    warnings.add(file.fileName() + ": bills not found yet for bill payments " + String.join(", ", missing));
                }
            }
        }
        return warnings;
    }

    @Transactional
    public BatchExecutionReport executeBatch(Long batchId, List<BatchFileRequest> files) {
        ImportBatchEntity batch = importBatchRepository.findByIdAndCompanyId(batchId, currentCompanyService.requireCurrentCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        batch.setStatus(ImportBatchStatus.RUNNING);
        batch.setUpdatedAt(Instant.now());
        importBatchRepository.save(batch);

        List<BatchFileRequest> ordered = files.stream()
                .sorted(Comparator.comparingInt((BatchFileRequest item) -> item.entityType().batchPriority())
                        .thenComparingInt(BatchFileRequest::position))
                .toList();

        List<ImportExecutionResult> results = new ArrayList<>();
        int completedFiles = 0;
        int runnableFiles = 0;

        for (BatchFileRequest file : ordered) {
            if (file.previewSummary() == null || file.previewSummary().hasBlockingIssues(file.skipInvalidRows()
                    ? ImportExecutionMode.IMPORT_READY_ONLY
                    : ImportExecutionMode.STRICT_ALL_ROWS)) {
                continue;
            }
            runnableFiles++;
            ImportExecutionResult result = workflowFacade.execute(
                    file.entityType(),
                    file.fileName(),
                    file.mappingProfileName(),
                    file.previewSummary().rawPreview(),
                    new ImportExecutionOptions(
                            batch,
                            file.position(),
                            dependencyGroup(file.entityType()),
                            file.skipInvalidRows() ? ImportExecutionMode.IMPORT_READY_ONLY : ImportExecutionMode.STRICT_ALL_ROWS));
            results.add(result);
            completedFiles++;
        }

        batch.setRunnableFiles(runnableFiles);
        batch.setCompletedFiles(completedFiles);
        batch.setValidatedFiles((int) files.stream().filter(file -> file.previewSummary() != null).count());
        boolean allSucceeded = results.stream().allMatch(ImportExecutionResult::success);
        batch.setStatus(allSucceeded ? ImportBatchStatus.COMPLETED : ImportBatchStatus.COMPLETED_WITH_ERRORS);
        batch.setUpdatedAt(Instant.now());
        importBatchRepository.save(batch);
        return new BatchExecutionReport(batch, ordered, results);
    }

    public List<ImportBatchEntity> recentBatches() {
        return importBatchRepository.findTop20ByCompanyIdOrderByCreatedAtDesc(currentCompanyService.requireCurrentCompanyId());
    }

    private Set<String> successfulIdentifiers(List<ImportRunEntity> runs, EntityType entityType) {
        Set<String> identifiers = new LinkedHashSet<>();
        runs.stream()
                .filter(run -> run.getEntityType() == entityType)
                .forEach(run -> run.getRowResults().stream()
                        .filter(row -> row.getStatus().name().equals("IMPORTED"))
                        .map(ImportRowResultEntity::getSourceIdentifier)
                        .filter(value -> value != null && !value.isBlank())
                        .forEach(identifiers::add));
        return identifiers;
    }

    private String dependencyGroup(EntityType entityType) {
        return switch (entityType) {
            case PAYMENT -> "invoice-dependency";
            case BILL_PAYMENT -> "bill-dependency";
            default -> "independent";
        };
    }

    public record BatchFileRequest(
            int position,
            EntityType entityType,
            String fileName,
            String mappingProfileName,
            ImportPreviewSummary previewSummary,
            boolean skipInvalidRows) {
    }

    public record BatchExecutionReport(
            ImportBatchEntity batch,
            List<BatchFileRequest> orderedFiles,
            List<ImportExecutionResult> results) {
    }
}
