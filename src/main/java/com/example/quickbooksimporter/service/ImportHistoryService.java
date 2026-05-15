package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import com.example.quickbooksimporter.repository.ImportBatchRepository;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

@Service
public class ImportHistoryService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> RAW_DATA_TYPE = new TypeReference<>() {};
    private static final List<String> FIXED_HEADERS = List.of(
            "runId", "entityType", "status", "sourceFileName", "mappingProfileName",
            "createdAt", "completedAt", "totalRows", "validRows", "invalidRows", "duplicateRows",
            "attemptedRows", "skippedRows", "importedRows", "batchId", "batchName", "batchOrder", "dependencyGroup",
            "rowNumber", "sourceIdentifier", "rowStatus", "message", "createdEntityId");

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

    public List<ImportRunEntity> recentCompletedRuns() {
        return recentRuns().stream()
                .filter(run -> run.getCompletedAt() != null && run.getAttemptedRows() > 0)
                .toList();
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

    public Optional<ImportRunEntity> findLatestRunForFile(EntityType entityType, String sourceFileName) {
        if (entityType == null || sourceFileName == null || sourceFileName.isBlank()) {
            return Optional.empty();
        }
        return importRunRepository.findTopByEntityTypeAndSourceFileNameAndCompanyIdOrderByCreatedAtDesc(
                entityType,
                sourceFileName,
                currentCompanyService.requireCurrentCompanyId());
    }

    public Optional<ImportBatchEntity> findBatch(Long batchId) {
        if (batchId == null) {
            return Optional.empty();
        }
        return importBatchRepository.findWithRunsByIdAndCompanyId(batchId, currentCompanyService.requireCurrentCompanyId());
    }

    public String buildRunExportCsv(ImportRunEntity run) {
        if (run == null) {
            throw new IllegalArgumentException("Import run is required");
        }
        List<ImportRowResultEntity> rowResults = run.getRowResults() == null ? List.of() : run.getRowResults();
        List<ImportRowResultEntity> orderedRows = rowResults.stream()
                .sorted(Comparator.comparingInt(ImportRowResultEntity::getRowNumber))
                .toList();
        Map<ImportRowResultEntity, Map<String, String>> parsedRawDataByRow = new LinkedHashMap<>();
        List<String> dynamicRawDataKeys = new ArrayList<>();
        for (ImportRowResultEntity row : orderedRows) {
            Map<String, String> parsed = parseRawData(row.getRawData());
            parsedRawDataByRow.put(row, parsed);
            for (String key : parsed.keySet()) {
                if (!dynamicRawDataKeys.contains(key)) {
                    dynamicRawDataKeys.add(key);
                }
            }
        }

        List<String> headers = new ArrayList<>(FIXED_HEADERS);
        dynamicRawDataKeys.forEach(key -> headers.add("userCsv." + key));
        headers.add("rawData");
        headers.add("normalizedData");

        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(headers.toArray(String[]::new))
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            if (orderedRows.isEmpty()) {
                printRecord(printer, run, null, Map.of(), dynamicRawDataKeys);
            } else {
                for (ImportRowResultEntity row : orderedRows) {
                    printRecord(printer, run, row, parsedRawDataByRow.getOrDefault(row, Map.of()), dynamicRawDataKeys);
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

    private void printRecord(CSVPrinter printer,
                             ImportRunEntity run,
                             ImportRowResultEntity row,
                             Map<String, String> rawDataFields,
                             List<String> dynamicRawDataKeys) throws IOException {
        List<Object> record = new ArrayList<>(FIXED_HEADERS.size() + dynamicRawDataKeys.size() + 2);
        record.add(run.getId());
        record.add(value(run.getEntityType()));
        record.add(value(run.getStatus()));
        record.add(run.getSourceFileName());
        record.add(run.getMappingProfileName());
        record.add(value(run.getCreatedAt()));
        record.add(value(run.getCompletedAt()));
        record.add(run.getTotalRows());
        record.add(run.getValidRows());
        record.add(run.getInvalidRows());
        record.add(run.getDuplicateRows());
        record.add(run.getAttemptedRows());
        record.add(run.getSkippedRows());
        record.add(run.getImportedRows());
        record.add(run.getBatch() == null ? null : run.getBatch().getId());
        record.add(run.getBatch() == null ? null : run.getBatch().getBatchName());
        record.add(run.getBatchOrder());
        record.add(run.getDependencyGroup());
        record.add(row == null ? null : row.getRowNumber());
        record.add(row == null ? null : row.getSourceIdentifier());
        record.add(row == null ? null : value(row.getStatus()));
        record.add(row == null ? null : row.getMessage());
        record.add(row == null ? null : row.getCreatedEntityId());
        for (String key : dynamicRawDataKeys) {
            record.add(row == null ? null : rawDataFields.getOrDefault(key, ""));
        }
        record.add(row == null ? null : row.getRawData());
        record.add(row == null ? null : row.getNormalizedData());
        printer.printRecord(record);
    }

    private Map<String, String> parseRawData(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(rawData, RAW_DATA_TYPE);
            Map<String, String> normalized = new LinkedHashMap<>();
            parsed.forEach((key, value) -> normalized.put(key, value == null ? "" : String.valueOf(value)));
            return normalized;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String value(Object input) {
        return input == null ? null : String.valueOf(input);
    }
}
