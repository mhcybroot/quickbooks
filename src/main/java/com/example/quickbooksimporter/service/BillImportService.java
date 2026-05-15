package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.BillImportPreview;
import com.example.quickbooksimporter.domain.BillImportPreviewRow;
import com.example.quickbooksimporter.domain.BillLine;
import com.example.quickbooksimporter.domain.BillRowValidationResult;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedBill;
import com.example.quickbooksimporter.domain.NormalizedBillField;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillImportService {
    private final InvoiceCsvParser parser;
    private final BillRowMapper rowMapper;
    private final BillImportValidator validator;
    private final ImportRunRepository importRunRepository;
    private final ObjectMapper objectMapper;
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;

    public BillImportService(InvoiceCsvParser parser, BillRowMapper rowMapper, BillImportValidator validator, ImportRunRepository importRunRepository, ObjectMapper objectMapper, QuickBooksConnectionService connectionService, QuickBooksGateway quickBooksGateway) {
        this.parser = parser;
        this.rowMapper = rowMapper;
        this.validator = validator;
        this.importRunRepository = importRunRepository;
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
        this.quickBooksGateway = quickBooksGateway;
    }

    public BillImportPreview preview(String fileName, byte[] bytes, Map<NormalizedBillField, String> mapping) {
        return preview(fileName, bytes, mapping, DateFormatOption.AUTO, PreviewProgressListener.noop(), false);
    }

    public BillImportPreview preview(String fileName,
                                     byte[] bytes,
                                     Map<NormalizedBillField, String> mapping,
                                     DateFormatOption dateFormatOption) {
        return preview(fileName, bytes, mapping, dateFormatOption, PreviewProgressListener.noop(), false);
    }

    public BillImportPreview preview(String fileName,
                                     byte[] bytes,
                                     Map<NormalizedBillField, String> mapping,
                                     DateFormatOption dateFormatOption,
                                     PreviewProgressListener progressListener,
                                     boolean skipQuickBooksChecks) {
        ParsedCsvDocument doc = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedBillField, String> finalMapping = new EnumMap<>(mapping);
        List<BillRowValidationResult> validations = validateGrouped(
                doc,
                finalMapping,
                dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption,
                progressListener == null ? PreviewProgressListener.noop() : progressListener,
                skipQuickBooksChecks);
        List<BillImportPreviewRow> rows = validations.stream().map(v -> new BillImportPreviewRow(
                v.rowNumber(),
                v.bill() == null ? "" : v.bill().billNo(),
                v.bill() == null ? "" : v.bill().vendor(),
                v.bill() == null ? 0 : v.bill().lines().size(),
                v.status(),
                v.message())).toList();
        return new BillImportPreview(fileName, finalMapping, doc.headers(), rows, validations);
    }

    public ImportExecutionResult execute(String fileName, String mappingProfileName, BillImportPreview preview) {
        return execute(fileName, mappingProfileName, preview, ImportExecutionOptions.standalone());
    }

    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         BillImportPreview preview,
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
        run.setEntityType(EntityType.BILL);
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
        run = importRunRepository.save(run);
        return doExecute(run, realmId, preview, mode);
    }

    @Transactional
    public Long preCreateRun(String fileName,
                             String mappingProfileName,
                             BillImportPreview preview,
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
        run.setEntityType(EntityType.BILL);
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

    public ImportExecutionResult executeWithRunId(Long runId,
                                                  String fileName,
                                                  String mappingProfileName,
                                                  BillImportPreview preview,
                                                  ImportExecutionOptions options) {
        ImportRunEntity run = importRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Import run not found: " + runId));
        String realmId = connectionService.getActiveConnection().getRealmId();
        return doExecute(run, realmId, preview, executionMode(options));
    }

    private ImportExecutionResult doExecute(ImportRunEntity run,
                                            String realmId,
                                            BillImportPreview preview,
                                            ImportExecutionMode mode) {
        int imported = 0;
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        int processedSinceFlush = 0;
        Instant lastFlushAt = Instant.now();

        List<BillRowValidationResult> validations = preview.validations();
        for (int i = 0; i < validations.size(); i += 20) {
            List<BillRowValidationResult> chunk = validations.subList(i, Math.min(validations.size(), i + 20));
            List<PreparedBillCreate> prepared = new ArrayList<>();
            for (BillRowValidationResult validation : chunk) {
                ImportRowResultEntity row = buildRow(run, validation);
                run.getRowResults().add(row);
                if (mode == ImportExecutionMode.IMPORT_READY_ONLY && validation.status() != ImportRowStatus.READY) {
                    row.setStatus(ImportRowStatus.SKIPPED);
                    row.setMessage("Skipped because row is not READY.");
                    skipped++;
                    processedSinceFlush++;
                } else {
                    attempted++;
                    try {
                        NormalizedBill bill = validation.bill();
                        quickBooksGateway.ensureVendor(realmId, bill.vendor());
                        for (BillLine line : bill.lines()) {
                            if (line.category() != null) {
                                quickBooksGateway.ensureExpenseCategory(realmId, line.category());
                            }
                        }
                        prepared.add(new PreparedBillCreate(bill, row));
                    } catch (Exception ex) {
                        row.setStatus(ImportRowStatus.FAILED);
                        row.setMessage(ex.getMessage());
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
                List<QuickBooksBatchCreateResult> results = quickBooksGateway.createBillsBatch(
                        realmId,
                        prepared.stream().map(PreparedBillCreate::bill).toList());
                for (int index = 0; index < prepared.size(); index++) {
                    PreparedBillCreate item = prepared.get(index);
                    QuickBooksBatchCreateResult result = results.get(index);
                    if (result.success()) {
                        item.row().setStatus(ImportRowStatus.IMPORTED);
                        item.row().setCreatedEntityId(result.entityId());
                        String label = result.referenceNumber() == null ? item.bill().billNo() : result.referenceNumber();
                        item.row().setMessage("Imported as QuickBooks bill " + label);
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
                ? "Imported " + imported + " bills."
                : "Imported " + imported + " ready bills; skipped " + skipped + " rows; " + failed + " failed during import. Check Import History for details.";
        return new ImportExecutionResult(saved, failed == 0, message);
    }

    private List<BillRowValidationResult> validateGrouped(ParsedCsvDocument doc,
                                                          Map<NormalizedBillField, String> mapping,
                                                          DateFormatOption dateFormatOption,
                                                          PreviewProgressListener progressListener,
                                                          boolean skipQuickBooksChecks) {
        Map<String, List<BillRowMapper.BillRowMapped>> groups = new HashMap<>();
        List<BillRowValidationResult> validations = new ArrayList<>();
        for (var row : doc.rows()) {
            try {
                var mapped = rowMapper.map(row, mapping, dateFormatOption);
                String key = mapped.billNo() == null ? "ROW-" + mapped.rowNumber() : mapped.billNo();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(mapped);
            } catch (Exception ex) {
                validations.add(new BillRowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, ex.getMessage(), row.values()));
            }
        }
        int totalGroups = groups.size() + validations.size();
        int completedGroups = validations.size();
        if (totalGroups > 0) {
            progressListener.onProgress(completedGroups, totalGroups, "Validated " + completedGroups + "/" + totalGroups + " bill groups");
        }
        for (List<BillRowMapper.BillRowMapped> group : groups.values()) {
            var first = group.getFirst();
            List<String> gErrors = new ArrayList<>();
            List<BillLine> lines = new ArrayList<>();
            for (var r : group) {
                if (!Objects.equals(first.vendor(), r.vendor()) || !Objects.equals(first.txnDate(), r.txnDate())
                        || !Objects.equals(first.dueDate(), r.dueDate()) || !Objects.equals(first.apAccount(), r.apAccount())) {
                    gErrors.add("Grouped rows for same bill must share vendor/date/due-date/AP-account");
                    break;
                }
                lines.add(r.line());
            }
            NormalizedBill bill = new NormalizedBill(first.billNo(), first.vendor(), first.txnDate(), first.dueDate(), first.apAccount(), lines);
            BillRowValidationResult v = validator.validate(first.rowNumber(), first.rawData(), bill, skipQuickBooksChecks);
            if (!gErrors.isEmpty()) {
                v = new BillRowValidationResult(v.rowNumber(), v.parsedRow(), v.bill(), ImportRowStatus.INVALID, v.message() + "; " + String.join("; ", gErrors), v.rawData());
            }
            validations.add(v);
            completedGroups++;
            progressListener.onProgress(completedGroups, totalGroups, "Validated " + completedGroups + "/" + totalGroups + " bill groups");
        }
        validations.sort(Comparator.comparingInt(BillRowValidationResult::rowNumber));
        return validations;
    }

    private ImportRunEntity persistRun(String fileName, String mappingProfileName, BillImportPreview preview, ImportRunStatus status, int importedRows) {
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.BILL);
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

    private ImportRowResultEntity buildRow(ImportRunEntity run, BillRowValidationResult validation) {
        ImportRowResultEntity row = new ImportRowResultEntity();
        row.setImportRun(run);
        row.setRowNumber(validation.rowNumber());
        row.setSourceIdentifier(validation.bill() == null ? null : validation.bill().billNo());
        row.setStatus(validation.status());
        row.setMessage(validation.message());
        row.setRawData(asJson(validation.rawData()));
        row.setNormalizedData(asJson(validation.bill()));
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

    private record PreparedBillCreate(NormalizedBill bill, ImportRowResultEntity row) {
    }
}
