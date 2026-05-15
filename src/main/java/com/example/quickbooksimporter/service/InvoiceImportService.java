package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportPreview;
import com.example.quickbooksimporter.domain.ImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.domain.RowValidationResult;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceImportService {

    private final InvoiceCsvParser parser;
    private final InvoiceRowMapper rowMapper;
    private final InvoiceImportValidator validator;
    private final CsvTemplateService csvTemplateService;
    private final ImportRunRepository importRunRepository;
    private final ObjectMapper objectMapper;
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;
    private final QuickBooksProperties quickBooksProperties;

    public InvoiceImportService(InvoiceCsvParser parser,
                                InvoiceRowMapper rowMapper,
                                InvoiceImportValidator validator,
                                CsvTemplateService csvTemplateService,
                                ImportRunRepository importRunRepository,
                                ObjectMapper objectMapper,
                                QuickBooksConnectionService connectionService,
                                QuickBooksGateway quickBooksGateway,
                                QuickBooksProperties quickBooksProperties) {
        this.parser = parser;
        this.rowMapper = rowMapper;
        this.validator = validator;
        this.csvTemplateService = csvTemplateService;
        this.importRunRepository = importRunRepository;
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
        this.quickBooksGateway = quickBooksGateway;
        this.quickBooksProperties = quickBooksProperties;
    }

    public ImportPreview preview(String fileName, byte[] bytes, Map<NormalizedInvoiceField, String> mapping) {
        return preview(fileName, bytes, mapping, false, DateFormatOption.AUTO, PreviewProgressListener.noop(), false);
    }

    public ImportPreview preview(String fileName,
                                 byte[] bytes,
                                 Map<NormalizedInvoiceField, String> mapping,
                                 boolean groupingEnabled) {
        return preview(fileName, bytes, mapping, groupingEnabled, DateFormatOption.AUTO, PreviewProgressListener.noop(), false);
    }

    public ImportPreview preview(String fileName,
                                 byte[] bytes,
                                 Map<NormalizedInvoiceField, String> mapping,
                                 boolean groupingEnabled,
                                 DateFormatOption dateFormatOption) {
        return preview(fileName, bytes, mapping, groupingEnabled, dateFormatOption, PreviewProgressListener.noop(), false);
    }

    public ImportPreview preview(String fileName,
                                 byte[] bytes,
                                 Map<NormalizedInvoiceField, String> mapping,
                                 boolean groupingEnabled,
                                 DateFormatOption dateFormatOption,
                                 PreviewProgressListener progressListener,
                                 boolean skipQuickBooksChecks) {
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedInvoiceField, String> finalMapping = new EnumMap<>(mapping);
        DateFormatOption effective = dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption;
        PreviewProgressListener listener = progressListener == null ? PreviewProgressListener.noop() : progressListener;
        List<RowValidationResult> validations = groupingEnabled
                ? validateGrouped(document, finalMapping, effective, listener, skipQuickBooksChecks)
                : validateUngrouped(document, finalMapping, effective, listener, skipQuickBooksChecks);
        List<ImportPreviewRow> rows = validations.stream()
                .map(result -> new ImportPreviewRow(
                        result.rowNumber(),
                        result.invoice() == null ? "" : result.invoice().invoiceNo(),
                        result.invoice() == null ? "" : result.invoice().customer(),
                        previewItemLabel(result.invoice()),
                        result.status(),
                        result.message()))
                .toList();
        String exportCsv = csvTemplateService.exportInvoices(validations.stream()
                .filter(result -> result.invoice() != null)
                .map(RowValidationResult::invoice)
                .toList());
        return new ImportPreview(fileName, finalMapping, document.headers(), rows, validations, exportCsv, groupingEnabled);
    }

    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         ImportPreview preview) {
        return execute(fileName, mappingProfileName, preview, ImportExecutionOptions.standalone());
    }

    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         ImportPreview preview,
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
        String preflightError = preflightAutoCreateRequirements(realmId, preview.validations());
        if (preflightError != null) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failedRun, false, preflightError);
        }
        int imported = 0;
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.INVOICE);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setExportCsv(preview.exportCsv());
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

    public List<ImportRunEntity> recentRuns() {
        return importRunRepository.findTop20ByCompanyIdOrderByCreatedAtDesc(connectionService.requireCurrentCompany().getId());
    }

    public ImportRunEntity getRun(long id) {
        return importRunRepository.findByIdAndCompanyId(id, connectionService.requireCurrentCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Import run not found"));
    }

    @Transactional
    public Long preCreateRun(String fileName,
                             String mappingProfileName,
                             ImportPreview preview,
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
        run.setEntityType(EntityType.INVOICE);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setExportCsv(preview.exportCsv());
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
                                                  ImportPreview preview,
                                                  ImportExecutionOptions options) {
        ImportRunEntity run = importRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Import run not found: " + runId));
        String realmId = connectionService.getActiveConnection().getRealmId();
        String preflightError = preflightAutoCreateRequirements(realmId, preview.validations());
        if (preflightError != null) {
            run.setStatus(ImportRunStatus.VALIDATION_FAILED);
            run.setCompletedAt(Instant.now());
            importRunRepository.save(run);
            return new ImportExecutionResult(run, false, preflightError);
        }
        return doExecute(run, realmId, preview, executionMode(options));
    }

    private ImportExecutionResult doExecute(ImportRunEntity run,
                                            String realmId,
                                            ImportPreview preview,
                                            ImportExecutionMode mode) {
        int imported = 0;
        int attempted = 0;
        int skipped = 0;
        int failed = 0;
        int processedSinceFlush = 0;
        Instant lastFlushAt = Instant.now();

        List<RowValidationResult> validations = preview.validations();
        for (int i = 0; i < validations.size(); i += 20) {
            List<RowValidationResult> chunk = validations.subList(i, Math.min(validations.size(), i + 20));
            List<PreparedInvoiceCreate> prepared = new ArrayList<>();
            for (RowValidationResult validation : chunk) {
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
                        NormalizedInvoice invoice = validation.invoice();
                        invoice.lines().forEach(line -> quickBooksGateway.ensureServiceItem(realmId, line.itemName(), line.description()));
                        quickBooksGateway.ensureCustomer(realmId, invoice.customer());
                        prepared.add(new PreparedInvoiceCreate(invoice, rowEntity));
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
                List<QuickBooksBatchCreateResult> results = quickBooksGateway.createInvoicesBatch(
                        realmId,
                        prepared.stream().map(PreparedInvoiceCreate::invoice).toList());
                for (int index = 0; index < prepared.size(); index++) {
                    PreparedInvoiceCreate item = prepared.get(index);
                    QuickBooksBatchCreateResult result = results.get(index);
                    if (result.success()) {
                        item.row().setStatus(ImportRowStatus.IMPORTED);
                        item.row().setCreatedEntityId(result.entityId());
                        String label = result.referenceNumber() == null ? item.invoice().invoiceNo() : result.referenceNumber();
                        item.row().setMessage("Imported as QuickBooks invoice " + label);
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
                ? "Imported " + imported + " invoices."
                : "Imported " + imported + " ready invoices; skipped " + skipped + " rows; " + failed + " failed during import. Check Import History for details.";
        return new ImportExecutionResult(saved, failed == 0, message);
    }

    private RowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                            Map<NormalizedInvoiceField, String> mapping,
                                            DateFormatOption dateFormatOption,
                                            boolean skipQuickBooksChecks) {
        try {
            return validator.validate(row.rowNumber(), row.values(), rowMapper.map(row, mapping, dateFormatOption), skipQuickBooksChecks);
        } catch (Exception exception) {
            return new RowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, exception.getMessage(), row.values());
        }
    }

    private List<RowValidationResult> validateUngrouped(ParsedCsvDocument document,
                                                        Map<NormalizedInvoiceField, String> mapping,
                                                        DateFormatOption dateFormatOption,
                                                        PreviewProgressListener progressListener,
                                                        boolean skipQuickBooksChecks) {
        List<RowValidationResult> validations = new ArrayList<>();
        int totalRows = document.rows().size();
        int completed = 0;
        if (totalRows > 0) {
            progressListener.onProgress(0, totalRows, "Validated 0/" + totalRows + " invoice rows");
        }
        for (var row : document.rows()) {
            validations.add(validateRow(row, mapping, dateFormatOption, skipQuickBooksChecks));
            completed++;
            progressListener.onProgress(completed, totalRows, "Validated " + completed + "/" + totalRows + " invoice rows");
        }
        return validations;
    }

    private List<RowValidationResult> validateGrouped(ParsedCsvDocument document,
                                                      Map<NormalizedInvoiceField, String> mapping,
                                                      DateFormatOption dateFormatOption,
                                                      PreviewProgressListener progressListener,
                                                      boolean skipQuickBooksChecks) {
        Map<String, List<GroupedInvoiceSource>> groups = new HashMap<>();
        List<RowValidationResult> validations = new ArrayList<>();
        for (var row : document.rows()) {
            try {
                NormalizedInvoice invoice = rowMapper.map(row, mapping, dateFormatOption);
                String key = invoice.invoiceNo() == null ? "ROW-" + row.rowNumber() : invoice.invoiceNo();
                groups.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new GroupedInvoiceSource(row.rowNumber(), row.values(), invoice));
            } catch (Exception exception) {
                validations.add(new RowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, exception.getMessage(), row.values()));
            }
        }
        int totalGroups = groups.size() + validations.size();
        int completedGroups = validations.size();
        if (totalGroups > 0) {
            progressListener.onProgress(completedGroups, totalGroups, "Validated " + completedGroups + "/" + totalGroups + " invoice groups");
        }

        for (List<GroupedInvoiceSource> group : groups.values()) {
            GroupedInvoiceSource first = group.getFirst();
            List<String> groupErrors = new ArrayList<>();
            List<InvoiceLine> lines = new ArrayList<>();
            for (GroupedInvoiceSource current : group) {
                if (!Objects.equals(first.invoice().customer(), current.invoice().customer())
                        || !Objects.equals(first.invoice().invoiceDate(), current.invoice().invoiceDate())
                        || !Objects.equals(first.invoice().dueDate(), current.invoice().dueDate())
                        || !Objects.equals(first.invoice().terms(), current.invoice().terms())
                        || !Objects.equals(first.invoice().location(), current.invoice().location())
                        || !Objects.equals(first.invoice().memo(), current.invoice().memo())) {
                    groupErrors.add("Grouped rows for the same invoice must share customer, invoice date, due date, terms, location, and memo");
                    break;
                }
                lines.addAll(current.invoice().lines());
            }
            NormalizedInvoice groupedInvoice = new NormalizedInvoice(
                    first.invoice().invoiceNo(),
                    first.invoice().customer(),
                    first.invoice().invoiceDate(),
                    first.invoice().dueDate(),
                    first.invoice().terms(),
                    first.invoice().location(),
                    first.invoice().memo(),
                    lines);
            RowValidationResult validation = validator.validate(first.rowNumber(), first.rawData(), groupedInvoice, skipQuickBooksChecks);
            if (!groupErrors.isEmpty()) {
                validation = new RowValidationResult(
                        validation.rowNumber(),
                        validation.parsedRow(),
                        validation.invoice(),
                        ImportRowStatus.INVALID,
                        joinMessages(validation.message(), String.join("; ", groupErrors)),
                        validation.rawData());
            }
            validations.add(validation);
            completedGroups++;
            progressListener.onProgress(completedGroups, totalGroups, "Validated " + completedGroups + "/" + totalGroups + " invoice groups");
        }
        validations.sort(Comparator.comparingInt(RowValidationResult::rowNumber));
        return validations;
    }

    private ImportRunEntity persistRun(String fileName,
                                       String mappingProfileName,
                                       ImportPreview preview,
                                       ImportRunStatus status,
                                       int importedRows) {
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.INVOICE);
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
        run.setExportCsv(preview.exportCsv());
        run.setCreatedAt(Instant.now());
        run.setCompany(connectionService.requireCurrentCompany());
        run.setCompletedAt(Instant.now());
        preview.validations().forEach(validation -> run.getRowResults().add(buildRow(run, validation)));
        return importRunRepository.save(run);
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

    private ImportRowResultEntity buildRow(ImportRunEntity run, RowValidationResult validation) {
        ImportRowResultEntity row = new ImportRowResultEntity();
        row.setImportRun(run);
        row.setRowNumber(validation.rowNumber());
        row.setSourceIdentifier(validation.invoice() == null ? null : validation.invoice().invoiceNo());
        row.setStatus(validation.status());
        row.setMessage(validation.message());
        row.setRawData(asJson(validation.rawData()));
        row.setNormalizedData(asJson(validation.invoice()));
        return row;
    }

    private String asJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize import data", exception);
        }
    }

    private String preflightAutoCreateRequirements(String realmId, List<RowValidationResult> validations) {
        if (quickBooksProperties.serviceItemIncomeAccountId() != null
                && !quickBooksProperties.serviceItemIncomeAccountId().isBlank()) {
            return null;
        }
        Set<String> missingItems = new LinkedHashSet<>();
        for (RowValidationResult validation : validations) {
            if (validation.invoice() == null) {
                continue;
            }
            for (var line : validation.invoice().lines()) {
                String itemName = line.itemName();
                if (itemName == null || itemName.isBlank()) {
                    continue;
                }
                if (!quickBooksGateway.serviceItemExists(realmId, itemName)) {
                    missingItems.add(itemName);
                }
            }
        }
        if (missingItems.isEmpty()) {
            return null;
        }
        return "Import blocked: missing QB_SERVICE_ITEM_INCOME_ACCOUNT_ID and these service items do not exist in QuickBooks: "
                + String.join(", ", missingItems);
    }

    private String previewItemLabel(NormalizedInvoice invoice) {
        if (invoice == null || invoice.lines().isEmpty()) {
            return "";
        }
        if (invoice.lines().size() == 1) {
            return invoice.lines().getFirst().itemName();
        }
        String firstItem = invoice.lines().getFirst().itemName();
        return firstItem + " +" + (invoice.lines().size() - 1) + " more";
    }

    private String joinMessages(String primary, String secondary) {
        if (primary == null || primary.isBlank()) {
            return secondary;
        }
        if (secondary == null || secondary.isBlank()) {
            return primary;
        }
        return primary + "; " + secondary;
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

    private record PreparedInvoiceCreate(NormalizedInvoice invoice, ImportRowResultEntity row) {
    }

    private record GroupedInvoiceSource(int rowNumber, Map<String, String> rawData, NormalizedInvoice invoice) {
    }
}
