package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillPaymentImportPreview;
import com.example.quickbooksimporter.domain.BillPaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.BillPaymentRowValidationResult;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedBillPayment;
import com.example.quickbooksimporter.domain.NormalizedBillPaymentField;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillPaymentImportService {
    private final InvoiceCsvParser parser;
    private final BillPaymentRowMapper rowMapper;
    private final BillPaymentImportValidator validator;
    private final ImportRunRepository importRunRepository;
    private final ObjectMapper objectMapper;
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;

    public BillPaymentImportService(InvoiceCsvParser parser, BillPaymentRowMapper rowMapper, BillPaymentImportValidator validator, ImportRunRepository importRunRepository, ObjectMapper objectMapper, QuickBooksConnectionService connectionService, QuickBooksGateway quickBooksGateway) {
        this.parser = parser;
        this.rowMapper = rowMapper;
        this.validator = validator;
        this.importRunRepository = importRunRepository;
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
        this.quickBooksGateway = quickBooksGateway;
    }

    public BillPaymentImportPreview preview(String fileName, byte[] bytes, Map<NormalizedBillPaymentField, String> mapping) {
        return preview(fileName, bytes, mapping, DateFormatOption.AUTO, PreviewProgressListener.noop());
    }

    public BillPaymentImportPreview preview(String fileName,
                                            byte[] bytes,
                                            Map<NormalizedBillPaymentField, String> mapping,
                                            DateFormatOption dateFormatOption) {
        return preview(fileName, bytes, mapping, dateFormatOption, PreviewProgressListener.noop());
    }

    public BillPaymentImportPreview preview(String fileName,
                                            byte[] bytes,
                                            Map<NormalizedBillPaymentField, String> mapping,
                                            DateFormatOption dateFormatOption,
                                            PreviewProgressListener progressListener) {
        ParsedCsvDocument doc = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedBillPaymentField, String> finalMapping = new EnumMap<>(mapping);
        Map<String, BigDecimal> allocatedByBill = new HashMap<>();
        DateFormatOption effective = dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption;
        PreviewProgressListener listener = progressListener == null ? PreviewProgressListener.noop() : progressListener;
        List<BillPaymentRowValidationResult> validations = new ArrayList<>();
        int totalRows = doc.rows().size();
        int completed = 0;
        if (totalRows > 0) {
            listener.onProgress(0, totalRows, "Validated 0/" + totalRows + " bill payment rows");
        }
        for (var row : doc.rows()) {
            validations.add(validateRow(row, finalMapping, allocatedByBill, effective));
            completed++;
            listener.onProgress(completed, totalRows, "Validated " + completed + "/" + totalRows + " bill payment rows");
        }
        List<BillPaymentImportPreviewRow> rows = validations.stream().map(v -> new BillPaymentImportPreviewRow(
                v.rowNumber(),
                v.payment() == null ? "" : v.payment().vendor(),
                v.payment() == null || v.payment().application() == null ? "" : v.payment().application().billNo(),
                v.payment() == null ? "" : v.payment().referenceNo(),
                v.status(),
                v.message())).toList();
        return new BillPaymentImportPreview(fileName, finalMapping, doc.headers(), rows, validations);
    }

    @Transactional
    public ImportExecutionResult execute(String fileName, String mappingProfileName, BillPaymentImportPreview preview) {
        return execute(fileName, mappingProfileName, preview, ImportExecutionOptions.standalone());
    }

    @Transactional
    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         BillPaymentImportPreview preview,
                                         ImportExecutionOptions options) {
        ImportExecutionMode mode = executionMode(options);
        long readyRows = preview.validations().stream().filter(v -> v.status() == ImportRowStatus.READY).count();
        if (mode == ImportExecutionMode.STRICT_ALL_ROWS
                && preview.validations().stream().anyMatch(v -> v.status() != ImportRowStatus.READY)) {
            ImportRunEntity failed = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failed, false, "Import blocked because one or more rows are invalid.");
        }
        if (mode == ImportExecutionMode.IMPORT_READY_ONLY && readyRows == 0) {
            ImportRunEntity failed = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failed, false, "Import blocked because there are no ready rows to import.");
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        int imported = 0;
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.BILL_PAYMENT);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setExportCsv(null);
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) readyRows);
        run.setInvalidRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.DUPLICATE).count());
        run.setAttemptedRows(0);
        run.setSkippedRows(0);
        run.setImportedRows(0);
        applyExecutionOptions(run, options);
        run = importRunRepository.save(run);
        int processedSinceFlush = 0;
        Instant lastFlushAt = Instant.now();
        List<PreparedBillPaymentCreate> prepared = new ArrayList<>();
        for (BillPaymentRowValidationResult validation : preview.validations()) {
            ImportRowResultEntity row = buildRow(run, validation);
            if (mode == ImportExecutionMode.IMPORT_READY_ONLY && validation.status() != ImportRowStatus.READY) {
                row.setStatus(ImportRowStatus.SKIPPED);
                row.setMessage("Skipped because row is not READY.");
                run.getRowResults().add(row);
                skipped++;
                processedSinceFlush++;
                ImportRunProgressFlusher.ProgressFlushResult flushResult = flushProgress(
                        run, attempted, skipped, imported, processedSinceFlush, lastFlushAt);
                lastFlushAt = flushResult.lastFlushAt();
                if (flushResult.flushed()) {
                    processedSinceFlush = 0;
                }
                continue;
            }
            run.getRowResults().add(row);
            attempted++;
            try {
                NormalizedBillPayment payment = validation.payment();
                QuickBooksBillRef billRef = quickBooksGateway.findBillByDocNumber(realmId, payment.application().billNo());
                prepared.add(new PreparedBillPaymentCreate(payment, row, billRef));
            } catch (Exception ex) {
                row.setStatus(ImportRowStatus.FAILED);
                row.setMessage(ex.getMessage());
                failed++;
                processedSinceFlush++;
                ImportRunProgressFlusher.ProgressFlushResult flushResult = flushProgress(
                        run, attempted, skipped, imported, processedSinceFlush, lastFlushAt);
                lastFlushAt = flushResult.lastFlushAt();
                if (flushResult.flushed()) {
                    processedSinceFlush = 0;
                }
            }
        }
        if (!prepared.isEmpty()) {
            List<QuickBooksBatchCreateResult> results = quickBooksGateway.createBillPaymentsBatch(
                    realmId,
                    prepared.stream().map(item -> new QuickBooksBillPaymentBatchCreateRequest(item.payment(), item.billRef())).toList());
            for (int index = 0; index < prepared.size(); index++) {
                PreparedBillPaymentCreate item = prepared.get(index);
                QuickBooksBatchCreateResult result = results.get(index);
                if (result.success()) {
                    item.row().setStatus(ImportRowStatus.IMPORTED);
                    item.row().setCreatedEntityId(result.entityId());
                    String label = result.referenceNumber() == null ? item.payment().referenceNo() : result.referenceNumber();
                    item.row().setMessage("Imported as QuickBooks bill payment " + label);
                    imported++;
                } else {
                    item.row().setStatus(ImportRowStatus.FAILED);
                    item.row().setMessage(result.message());
                    failed++;
                }
                processedSinceFlush++;
                ImportRunProgressFlusher.ProgressFlushResult flushResult = flushProgress(
                        run, attempted, skipped, imported, processedSinceFlush, lastFlushAt);
                lastFlushAt = flushResult.lastFlushAt();
                if (flushResult.flushed()) {
                    processedSinceFlush = 0;
                }
            }
        }
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) readyRows);
        run.setInvalidRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.DUPLICATE).count());
        run.setAttemptedRows(attempted);
        run.setSkippedRows(skipped);
        run.setImportedRows(imported);
        run.setStatus(failed == 0 && skipped == 0 ? ImportRunStatus.IMPORTED : ImportRunStatus.PARTIAL_FAILURE);
        run.setCompletedAt(Instant.now());
        ImportRunEntity saved = importRunRepository.save(run);
        String message = failed == 0 && skipped == 0
                ? "Imported " + imported + " bill payments."
                : "Imported " + imported + " ready bill payments; skipped " + skipped + " rows; " + failed + " failed during import. Check Import History for details.";
        return new ImportExecutionResult(saved, failed == 0, message);
    }

    @Transactional
    public Long preCreateRun(String fileName,
                             String mappingProfileName,
                             BillPaymentImportPreview preview,
                             ImportExecutionOptions options) {
        ImportExecutionMode mode = executionMode(options);
        long readyRows = preview.validations().stream().filter(v -> v.status() == ImportRowStatus.READY).count();
        if (mode == ImportExecutionMode.STRICT_ALL_ROWS
                && preview.validations().stream().anyMatch(v -> v.status() != ImportRowStatus.READY)) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return failedRun.getId();
        }
        if (mode == ImportExecutionMode.IMPORT_READY_ONLY && readyRows == 0) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return failedRun.getId();
        }
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.BILL_PAYMENT);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setExportCsv(null);
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) readyRows);
        run.setInvalidRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.DUPLICATE).count());
        run.setAttemptedRows(0);
        run.setSkippedRows(0);
        run.setImportedRows(0);
        applyExecutionOptions(run, options);
        run = importRunRepository.save(run);
        return run.getId();
    }

    @Transactional
    public ImportExecutionResult executeWithRunId(Long runId,
                                                  String fileName,
                                                  String mappingProfileName,
                                                  BillPaymentImportPreview preview,
                                                  ImportExecutionOptions options) {
        ImportRunEntity run = importRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Import run not found: " + runId));
        String realmId = connectionService.getActiveConnection().getRealmId();
        int imported = 0;
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        int processedSinceFlush = 0;
        Instant lastFlushAt = Instant.now();
        List<PreparedBillPaymentCreate> prepared = new ArrayList<>();
        for (BillPaymentRowValidationResult validation : preview.validations()) {
            ImportRowResultEntity row = buildRow(run, validation);
            if (validation.status() != ImportRowStatus.READY) {
                row.setStatus(ImportRowStatus.SKIPPED);
                row.setMessage("Skipped because row is not READY.");
                run.getRowResults().add(row);
                skipped++;
                processedSinceFlush++;
                ImportRunProgressFlusher.ProgressFlushResult flushResult = flushProgress(
                        run, attempted, skipped, imported, processedSinceFlush, lastFlushAt);
                lastFlushAt = flushResult.lastFlushAt();
                if (flushResult.flushed()) {
                    processedSinceFlush = 0;
                }
                continue;
            }
            run.getRowResults().add(row);
            attempted++;
            try {
                NormalizedBillPayment payment = validation.payment();
                QuickBooksBillRef billRef = quickBooksGateway.findBillByDocNumber(realmId, payment.application().billNo());
                prepared.add(new PreparedBillPaymentCreate(payment, row, billRef));
            } catch (Exception ex) {
                row.setStatus(ImportRowStatus.FAILED);
                row.setMessage(ex.getMessage());
                failed++;
                processedSinceFlush++;
                ImportRunProgressFlusher.ProgressFlushResult flushResult = flushProgress(
                        run, attempted, skipped, imported, processedSinceFlush, lastFlushAt);
                lastFlushAt = flushResult.lastFlushAt();
                if (flushResult.flushed()) {
                    processedSinceFlush = 0;
                }
            }
        }
        if (!prepared.isEmpty()) {
            List<QuickBooksBatchCreateResult> results = quickBooksGateway.createBillPaymentsBatch(
                    realmId,
                    prepared.stream().map(item -> new QuickBooksBillPaymentBatchCreateRequest(item.payment(), item.billRef())).toList());
            for (int index = 0; index < prepared.size(); index++) {
                PreparedBillPaymentCreate item = prepared.get(index);
                QuickBooksBatchCreateResult result = results.get(index);
                if (result.success()) {
                    item.row().setStatus(ImportRowStatus.IMPORTED);
                    item.row().setCreatedEntityId(result.entityId());
                    String label = result.referenceNumber() == null ? item.payment().referenceNo() : result.referenceNumber();
                    item.row().setMessage("Imported as QuickBooks bill payment " + label);
                    imported++;
                } else {
                    item.row().setStatus(ImportRowStatus.FAILED);
                    item.row().setMessage(result.message());
                    failed++;
                }
                processedSinceFlush++;
                ImportRunProgressFlusher.ProgressFlushResult flushResult = flushProgress(
                        run, attempted, skipped, imported, processedSinceFlush, lastFlushAt);
                lastFlushAt = flushResult.lastFlushAt();
                if (flushResult.flushed()) {
                    processedSinceFlush = 0;
                }
            }
        }
        long readyRows = preview.validations().stream().filter(v -> v.status() == ImportRowStatus.READY).count();
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) readyRows);
        run.setInvalidRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.DUPLICATE).count());
        run.setAttemptedRows(attempted);
        run.setSkippedRows(skipped);
        run.setImportedRows(imported);
        run.setStatus(failed == 0 && skipped == 0 ? ImportRunStatus.IMPORTED : ImportRunStatus.PARTIAL_FAILURE);
        run.setCompletedAt(Instant.now());
        ImportRunEntity saved = importRunRepository.save(run);
        String message = failed == 0 && skipped == 0
                ? "Imported " + imported + " bill payments."
                : "Imported " + imported + " ready bill payments; skipped " + skipped + " rows; " + failed + " failed during import. Check Import History for details.";
        return new ImportExecutionResult(saved, failed == 0, message);
    }

    private BillPaymentRowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                                       Map<NormalizedBillPaymentField, String> mapping,
                                                       Map<String, BigDecimal> allocatedByBill,
                                                       DateFormatOption dateFormatOption) {
        try {
            NormalizedBillPayment payment = rowMapper.map(row, mapping, dateFormatOption);
            String billNo = payment == null || payment.application() == null ? null : payment.application().billNo();
            BigDecimal alreadyAllocated = billNo == null ? BigDecimal.ZERO : allocatedByBill.getOrDefault(billNo, BigDecimal.ZERO);
            BillPaymentRowValidationResult result = validator.validate(row.rowNumber(), row.values(), payment, alreadyAllocated);
            if (result.status() == ImportRowStatus.READY && billNo != null && payment.application().appliedAmount() != null) {
                allocatedByBill.merge(billNo, payment.application().appliedAmount(), BigDecimal::add);
            }
            return result;
        }
        catch (Exception ex) { return new BillPaymentRowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, ex.getMessage(), row.values()); }
    }

    private ImportRunEntity persistRun(String fileName, String mappingProfileName, BillPaymentImportPreview preview, ImportRunStatus status, int importedRows) {
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.BILL_PAYMENT);
        run.setStatus(status);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setTotalRows(preview.rows().size());
        run.setValidRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.READY).count());
        run.setInvalidRows((int) preview.validations().stream().filter(v -> v.status() == ImportRowStatus.INVALID).count());
        run.setDuplicateRows((int) preview.validations().stream().filter(v -> v.message().contains("already exists")).count());
        run.setImportedRows(importedRows);
        run.setAttemptedRows(0);
        run.setSkippedRows(0);
        run.setExportCsv(null);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setCompletedAt(Instant.now());
        preview.validations().forEach(v -> run.getRowResults().add(buildRow(run, v)));
        return importRunRepository.save(run);
    }

    private ImportRowResultEntity buildRow(ImportRunEntity run, BillPaymentRowValidationResult validation) {
        ImportRowResultEntity row = new ImportRowResultEntity();
        row.setImportRun(run);
        row.setRowNumber(validation.rowNumber());
        row.setSourceIdentifier(validation.payment() == null ? null : validation.payment().referenceNo());
        row.setStatus(validation.status());
        row.setMessage(validation.message());
        row.setRawData(asJson(validation.rawData()));
        row.setNormalizedData(asJson(validation.payment()));
        return row;
    }

    private String asJson(Object value) {
        try { return value == null ? null : objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize import data", ex); }
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

    private record PreparedBillPaymentCreate(NormalizedBillPayment payment, ImportRowResultEntity row, QuickBooksBillRef billRef) {
    }
}
