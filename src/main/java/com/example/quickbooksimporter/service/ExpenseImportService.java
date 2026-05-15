package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ExpenseImportPreview;
import com.example.quickbooksimporter.domain.ExpenseImportPreviewRow;
import com.example.quickbooksimporter.domain.ExpenseRowValidationResult;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedExpense;
import com.example.quickbooksimporter.domain.NormalizedExpenseField;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseImportService {

    private final InvoiceCsvParser parser;
    private final ExpenseRowMapper rowMapper;
    private final ExpenseImportValidator validator;
    private final ImportRunRepository importRunRepository;
    private final ObjectMapper objectMapper;
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;

    public ExpenseImportService(InvoiceCsvParser parser,
                                ExpenseRowMapper rowMapper,
                                ExpenseImportValidator validator,
                                ImportRunRepository importRunRepository,
                                ObjectMapper objectMapper,
                                QuickBooksConnectionService connectionService,
                                QuickBooksGateway quickBooksGateway) {
        this.parser = parser;
        this.rowMapper = rowMapper;
        this.validator = validator;
        this.importRunRepository = importRunRepository;
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
        this.quickBooksGateway = quickBooksGateway;
    }

    public ExpenseImportPreview preview(String fileName, byte[] bytes, Map<NormalizedExpenseField, String> mapping) {
        return preview(fileName, bytes, mapping, DateFormatOption.AUTO, PreviewProgressListener.noop());
    }

    public ExpenseImportPreview preview(String fileName,
                                        byte[] bytes,
                                        Map<NormalizedExpenseField, String> mapping,
                                        DateFormatOption dateFormatOption) {
        return preview(fileName, bytes, mapping, dateFormatOption, PreviewProgressListener.noop());
    }

    public ExpenseImportPreview preview(String fileName,
                                        byte[] bytes,
                                        Map<NormalizedExpenseField, String> mapping,
                                        DateFormatOption dateFormatOption,
                                        PreviewProgressListener progressListener) {
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedExpenseField, String> finalMapping = new EnumMap<>(mapping);
        DateFormatOption effective = dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption;
        PreviewProgressListener listener = progressListener == null ? PreviewProgressListener.noop() : progressListener;
        List<ExpenseRowValidationResult> validations = new ArrayList<>();
        int totalRows = document.rows().size();
        int completed = 0;
        if (totalRows > 0) {
            listener.onProgress(0, totalRows, "Validated 0/" + totalRows + " expense rows");
        }
        for (var row : document.rows()) {
            validations.add(validateRow(row, finalMapping, effective));
            completed++;
            listener.onProgress(completed, totalRows, "Validated " + completed + "/" + totalRows + " expense rows");
        }
        List<ExpenseImportPreviewRow> rows = validations.stream()
                .map(result -> new ExpenseImportPreviewRow(
                        result.rowNumber(),
                        result.expense() == null ? "" : result.expense().vendor(),
                        result.expense() == null ? "" : result.expense().category(),
                        result.expense() == null ? "" : result.expense().referenceNo(),
                        result.status(),
                        result.message()))
                .toList();
        return new ExpenseImportPreview(fileName, finalMapping, document.headers(), rows, validations);
    }

    public ImportExecutionResult execute(String fileName, String mappingProfileName, ExpenseImportPreview preview) {
        return execute(fileName, mappingProfileName, preview, ImportExecutionOptions.standalone());
    }

    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         ExpenseImportPreview preview,
                                         ImportExecutionOptions options) {
        ImportExecutionMode mode = executionMode(options);
        long readyRows = preview.validations().stream().filter(result -> result.status() == ImportRowStatus.READY).count();
        if (mode == ImportExecutionMode.STRICT_ALL_ROWS
                && preview.validations().stream().anyMatch(result -> result.status() != ImportRowStatus.READY)) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failedRun, false, "Import blocked because one or more rows are invalid.");
        }
        if (mode == ImportExecutionMode.IMPORT_READY_ONLY && readyRows == 0) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failedRun, false, "Import blocked because there are no ready rows to import.");
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        int imported = 0;
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.EXPENSE);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setExportCsv(null);
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) readyRows);
        run.setInvalidRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.DUPLICATE).count());
        run.setAttemptedRows(0);
        run.setSkippedRows(0);
        run.setImportedRows(0);
        applyExecutionOptions(run, options);
        run = importRunRepository.save(run);

        run = importRunRepository.save(run);
        return doExecute(run, realmId, preview, mode);
    }

    @Transactional
    public Long preCreateRun(String fileName,
                             String mappingProfileName,
                             ExpenseImportPreview preview,
                             ImportExecutionOptions options) {
        ImportExecutionMode mode = executionMode(options);
        long readyRows = preview.validations().stream().filter(result -> result.status() == ImportRowStatus.READY).count();
        if (mode == ImportExecutionMode.STRICT_ALL_ROWS
                && preview.validations().stream().anyMatch(result -> result.status() != ImportRowStatus.READY)) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return failedRun.getId();
        }
        if (mode == ImportExecutionMode.IMPORT_READY_ONLY && readyRows == 0) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return failedRun.getId();
        }
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.EXPENSE);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setExportCsv(null);
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) readyRows);
        run.setInvalidRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.DUPLICATE).count());
        run.setAttemptedRows(0);
        run.setSkippedRows(0);
        run.setImportedRows(0);
        applyExecutionOptions(run, options);
        run = importRunRepository.save(run);
        return run.getId();
    }

    public ImportExecutionResult executeWithRunId(Long runId,
                                                  String fileName,
                                                  String mappingProfileName,
                                                  ExpenseImportPreview preview,
                                                  ImportExecutionOptions options) {
        ImportRunEntity run = importRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Import run not found: " + runId));
        String realmId = connectionService.getActiveConnection().getRealmId();
        return doExecute(run, realmId, preview, executionMode(options));
    }

    private ImportExecutionResult doExecute(ImportRunEntity run,
                                            String realmId,
                                            ExpenseImportPreview preview,
                                            ImportExecutionMode mode) {
        int imported = 0;
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        int processedSinceFlush = 0;
        Instant lastFlushAt = Instant.now();

        List<ExpenseRowValidationResult> validations = preview.validations();
        for (int i = 0; i < validations.size(); i += 20) {
            List<ExpenseRowValidationResult> chunk = validations.subList(i, Math.min(validations.size(), i + 20));
            List<PreparedExpenseCreate> prepared = new ArrayList<>();
            for (ExpenseRowValidationResult validation : chunk) {
                ImportRowResultEntity rowEntity = buildRow(run, validation);
                run.getRowResults().add(rowEntity);
                if (mode == ImportExecutionMode.IMPORT_READY_ONLY && validation.status() != ImportRowStatus.READY) {
                    rowEntity.setStatus(ImportRowStatus.SKIPPED);
                    rowEntity.setMessage("Skipped because row is not READY.");
                    skipped++;
                    processedSinceFlush++;
                } else {
                    attempted++;
                    try {
                        NormalizedExpense expense = validation.expense();
                        quickBooksGateway.ensureVendor(realmId, expense.vendor());
                        quickBooksGateway.ensureExpenseCategory(realmId, expense.category());
                        prepared.add(new PreparedExpenseCreate(expense, rowEntity));
                    } catch (Exception exception) {
                        rowEntity.setStatus(ImportRowStatus.FAILED);
                        rowEntity.setMessage(exception.getMessage());
                        failed++;
                    }
                    processedSinceFlush++;
                }
                
                ImportRunProgressFlusher.ProgressFlushResult prepFlushResult = flushProgress(
                        run, attempted, skipped, imported, processedSinceFlush, lastFlushAt);
                lastFlushAt = prepFlushResult.lastFlushAt();
                if (prepFlushResult.flushed()) {
                    processedSinceFlush = 0;
                }
            }

            if (!prepared.isEmpty()) {
                List<QuickBooksBatchCreateResult> results = quickBooksGateway.createExpensesBatch(
                        realmId,
                        prepared.stream().map(PreparedExpenseCreate::expense).toList());
                for (int index = 0; index < prepared.size(); index++) {
                    PreparedExpenseCreate item = prepared.get(index);
                    QuickBooksBatchCreateResult result = results.get(index);
                    if (result.success()) {
                        item.row().setStatus(ImportRowStatus.IMPORTED);
                        item.row().setCreatedEntityId(result.entityId());
                        String label = result.referenceNumber() == null ? item.expense().referenceNo() : result.referenceNumber();
                        item.row().setMessage("Imported as QuickBooks expense " + label);
                        imported++;
                    } else {
                        item.row().setStatus(ImportRowStatus.FAILED);
                        item.row().setMessage(result.message());
                        failed++;
                    }
                    processedSinceFlush++;
                }
            }

            ImportRunProgressFlusher.ProgressFlushResult flushResult = flushProgress(
                    run, attempted, skipped, imported, 5, lastFlushAt);
            lastFlushAt = flushResult.lastFlushAt();
            if (flushResult.flushed()) {
                processedSinceFlush = 0;
            }
        }

        long readyRows = preview.validations().stream().filter(result -> result.status() == ImportRowStatus.READY).count();
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) readyRows);
        run.setInvalidRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.DUPLICATE).count());
        run.setAttemptedRows(attempted);
        run.setSkippedRows(skipped);
        run.setImportedRows(imported);
        run.setStatus(failed == 0 && skipped == 0 ? ImportRunStatus.IMPORTED : ImportRunStatus.PARTIAL_FAILURE);
        run.setCompletedAt(Instant.now());
        ImportRunEntity saved = importRunRepository.save(run);
        String message = failed == 0 && skipped == 0
                ? "Imported " + imported + " expenses."
                : "Imported " + imported + " ready expenses; skipped " + skipped + " rows; " + failed + " failed during import. Check Import History for details.";
        return new ImportExecutionResult(saved, failed == 0, message);
    }

    private ExpenseRowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                                   Map<NormalizedExpenseField, String> mapping,
                                                   DateFormatOption dateFormatOption) {
        try {
            return validator.validate(row.rowNumber(), row.values(), rowMapper.map(row, mapping, dateFormatOption));
        } catch (Exception exception) {
            return new ExpenseRowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, exception.getMessage(), row.values());
        }
    }

    private ImportRunEntity persistRun(String fileName,
                                       String mappingProfileName,
                                       ExpenseImportPreview preview,
                                       ImportRunStatus status,
                                       int importedRows) {
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.EXPENSE);
        run.setStatus(status);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.READY).count());
        run.setInvalidRows((int) preview.validations().stream().filter(result -> result.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(result -> result.message().contains("already exists")).count());
        run.setImportedRows(importedRows);
        run.setAttemptedRows(0);
        run.setSkippedRows(0);
        run.setExportCsv(null);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setCompletedAt(Instant.now());
        preview.validations().forEach(validation -> run.getRowResults().add(buildRow(run, validation)));
        return importRunRepository.save(run);
    }

    private ImportRowResultEntity buildRow(ImportRunEntity run, ExpenseRowValidationResult validation) {
        ImportRowResultEntity row = new ImportRowResultEntity();
        row.setImportRun(run);
        row.setRowNumber(validation.rowNumber());
        row.setSourceIdentifier(validation.expense() == null ? null : validation.expense().referenceNo());
        row.setStatus(validation.status());
        row.setMessage(validation.message());
        row.setRawData(asJson(validation.rawData()));
        row.setNormalizedData(asJson(validation.expense()));
        return row;
    }

    private String asJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize import data", exception);
        }
    }

    private void applyExecutionOptions(ImportRunEntity run, ImportExecutionOptions options) {
        if (options == null) {
            return;
        }
        run.setBatch(options.batch());
        run.setBatchOrder(options.batchOrder());
        run.setDependencyGroup(options.dependencyGroup());
    }

    private ImportExecutionMode executionMode(ImportExecutionOptions options) {
        if (options == null || options.executionMode() == null) {
            return ImportExecutionMode.STRICT_ALL_ROWS;
        }
        return options.executionMode();
    }

    private ImportRunProgressFlusher.ProgressFlushResult flushProgress(ImportRunEntity run,
                                                                       int attempted,
                                                                       int skipped,
                                                                       int imported,
                                                                       int processedSinceFlush,
                                                                       Instant lastFlushAt) {
        return ImportRunProgressFlusher.flushProgress(
                importRunRepository,
                run,
                attempted,
                skipped,
                imported,
                processedSinceFlush,
                lastFlushAt);
    }

    private record PreparedExpenseCreate(NormalizedExpense expense, ImportRowResultEntity row) {
    }
}
