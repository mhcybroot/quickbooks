package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import java.io.IOException;
import java.io.StringWriter;
import com.example.quickbooksimporter.repository.ImportBatchRepository;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

@Service
public class ImportHistoryService {

    private final ImportRunRepository importRunRepository;
    private final ImportBatchRepository importBatchRepository;
    private final CurrentCompanyService currentCompanyService;

    public ImportHistoryService(ImportRunRepository importRunRepository,
                                ImportBatchRepository importBatchRepository,
                                CurrentCompanyService currentCompanyService) {
        this.importRunRepository = importRunRepository;
        this.importBatchRepository = importBatchRepository;
        this.currentCompanyService = currentCompanyService;
    }

    public List<ImportRunEntity> recentRuns() {
        return importRunRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(currentCompanyService.requireCurrentCompanyId());
    }

    public List<ImportBatchEntity> recentBatches() {
        return importBatchRepository.findTop20ByCompanyIdOrderByCreatedAtDesc(currentCompanyService.requireCurrentCompanyId());
    }

    public List<ImportRunEntity> runsForBatch(Long batchId) {
        if (batchId == null) {
            return List.of();
        }
        return importRunRepository.findByBatchIdAndCompanyIdOrderByBatchOrderAscCreatedAtAsc(batchId, currentCompanyService.requireCurrentCompanyId());
    }

    public Optional<ImportRunEntity> findRun(Long runId) {
        if (runId == null) {
            return Optional.empty();
        }
        return importRunRepository.findByIdAndCompanyId(runId, currentCompanyService.requireCurrentCompanyId());
    }

    public String buildRunExportCsv(ImportRunEntity run) {
        if (run == null) {
            throw new IllegalArgumentException("Import run is required");
        }
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(
                        "runId", "entityType", "status", "sourceFileName", "mappingProfileName",
                        "createdAt", "completedAt", "totalRows", "validRows", "invalidRows", "duplicateRows",
                        "attemptedRows", "skippedRows", "importedRows", "batchId", "batchName", "batchOrder", "dependencyGroup",
                        "rowNumber", "sourceIdentifier", "rowStatus", "message", "createdEntityId", "rawData", "normalizedData")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            List<ImportRowResultEntity> rowResults = run.getRowResults() == null ? List.of() : run.getRowResults();
            if (rowResults.isEmpty()) {
                printRecord(printer, run, null);
            } else {
                for (ImportRowResultEntity row : rowResults) {
                    printRecord(printer, run, row);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export import run CSV", exception);
        }
        return writer.toString();
    }

    public String runExportFileName(ImportRunEntity run) {
        if (run == null || run.getId() == null || run.getEntityType() == null) {
            throw new IllegalArgumentException("Import run id and entity type are required");
        }
        return "import-run-" + run.getId() + "-" + run.getEntityType().name().toLowerCase() + ".csv";
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

    private void printRecord(CSVPrinter printer, ImportRunEntity run, ImportRowResultEntity row) throws IOException {
        printer.printRecord(
                run.getId(),
                value(run.getEntityType()),
                value(run.getStatus()),
                run.getSourceFileName(),
                run.getMappingProfileName(),
                value(run.getCreatedAt()),
                value(run.getCompletedAt()),
                run.getTotalRows(),
                run.getValidRows(),
                run.getInvalidRows(),
                run.getDuplicateRows(),
                run.getAttemptedRows(),
                run.getSkippedRows(),
                run.getImportedRows(),
                run.getBatch() == null ? null : run.getBatch().getId(),
                run.getBatch() == null ? null : run.getBatch().getBatchName(),
                run.getBatchOrder(),
                run.getDependencyGroup(),
                row == null ? null : row.getRowNumber(),
                row == null ? null : row.getSourceIdentifier(),
                row == null ? null : value(row.getStatus()),
                row == null ? null : row.getMessage(),
                row == null ? null : row.getCreatedEntityId(),
                row == null ? null : row.getRawData(),
                row == null ? null : row.getNormalizedData());
    }

    private String value(Object input) {
        return input == null ? null : String.valueOf(input);
    }
}
