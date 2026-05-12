package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedSalesReceipt;
import com.example.quickbooksimporter.domain.NormalizedSalesReceiptField;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreview;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreviewRow;
import com.example.quickbooksimporter.domain.SalesReceiptLine;
import com.example.quickbooksimporter.domain.SalesReceiptRowValidationResult;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesReceiptImportService {

    private final InvoiceCsvParser parser;
    private final SalesReceiptRowMapper rowMapper;
    private final SalesReceiptImportValidator validator;
    private final ImportRunRepository importRunRepository;
    private final ObjectMapper objectMapper;
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;

    public SalesReceiptImportService(InvoiceCsvParser parser,
                                     SalesReceiptRowMapper rowMapper,
                                     SalesReceiptImportValidator validator,
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

    public SalesReceiptImportPreview preview(String fileName, byte[] bytes, Map<NormalizedSalesReceiptField, String> mapping) {
        return preview(fileName, bytes, mapping, DateFormatOption.AUTO);
    }

    public SalesReceiptImportPreview preview(String fileName,
                                             byte[] bytes,
                                             Map<NormalizedSalesReceiptField, String> mapping,
                                             DateFormatOption dateFormatOption) {
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedSalesReceiptField, String> finalMapping = new EnumMap<>(mapping);
        List<SalesReceiptRowValidationResult> validations = validateGrouped(
                document,
                finalMapping,
                dateFormatOption == null ? DateFormatOption.AUTO : dateFormatOption);
        List<SalesReceiptImportPreviewRow> rows = validations.stream()
                .map(result -> new SalesReceiptImportPreviewRow(
                        result.rowNumber(),
                        result.salesReceipt() == null ? "" : result.salesReceipt().receiptNo(),
                        result.salesReceipt() == null ? "" : result.salesReceipt().customer(),
                        result.salesReceipt() == null ? 0 : result.salesReceipt().lines().size(),
                        result.status(),
                        result.message()))
                .toList();
        return new SalesReceiptImportPreview(fileName, finalMapping, document.headers(), rows, validations);
    }

    @Transactional
    public ImportExecutionResult execute(String fileName, String mappingProfileName, SalesReceiptImportPreview preview) {
        return execute(fileName, mappingProfileName, preview, ImportExecutionOptions.standalone());
    }

    @Transactional
    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         SalesReceiptImportPreview preview,
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
        run.setEntityType(EntityType.SALES_RECEIPT);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setExportCsv(null);
        applyExecutionOptions(run, options);

        for (SalesReceiptRowValidationResult validation : preview.validations()) {
            ImportRowResultEntity rowEntity = buildRow(run, validation);
            if (mode == ImportExecutionMode.IMPORT_READY_ONLY && validation.status() != ImportRowStatus.READY) {
                rowEntity.setStatus(ImportRowStatus.SKIPPED);
                rowEntity.setMessage("Skipped because row is not READY.");
                run.getRowResults().add(rowEntity);
                skipped++;
                continue;
            }
            try {
                attempted++;
                NormalizedSalesReceipt receipt = validation.salesReceipt();
                if (!StringUtils.isBlank(receipt.paymentMethod())) {
                    quickBooksGateway.ensurePaymentMethod(realmId, receipt.paymentMethod());
                }
                QuickBooksSalesReceiptCreateResult created = quickBooksGateway.createSalesReceipt(realmId, receipt);
                rowEntity.setStatus(ImportRowStatus.IMPORTED);
                rowEntity.setCreatedEntityId(created.salesReceiptId());
                String label = created.docNumber() == null ? receipt.receiptNo() : created.docNumber();
                rowEntity.setMessage("Imported as QuickBooks sales receipt " + label);
                imported++;
            } catch (Exception exception) {
                rowEntity.setStatus(ImportRowStatus.FAILED);
                rowEntity.setMessage(exception.getMessage());
                failed++;
            }
            run.getRowResults().add(rowEntity);
        }
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
                ? "Imported " + imported + " sales receipts."
                : "Imported " + imported + " ready sales receipts; skipped " + skipped + " rows; " + failed + " failed during import. Check Import History for details.";
        return new ImportExecutionResult(saved, failed == 0, message);
    }

    private List<SalesReceiptRowValidationResult> validateGrouped(ParsedCsvDocument document,
                                                                  Map<NormalizedSalesReceiptField, String> mapping,
                                                                  DateFormatOption dateFormatOption) {
        Map<String, List<SalesReceiptRowMapper.SalesReceiptRowMapped>> groups = new HashMap<>();
        List<SalesReceiptRowValidationResult> failures = new ArrayList<>();

        for (var row : document.rows()) {
            try {
                SalesReceiptRowMapper.SalesReceiptRowMapped mapped = rowMapper.map(row, mapping, dateFormatOption);
                String key = mapped.receiptNo() == null ? "ROW-" + mapped.rowNumber() : mapped.receiptNo();
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(mapped);
            } catch (Exception exception) {
                failures.add(new SalesReceiptRowValidationResult(
                        row.rowNumber(),
                        row,
                        null,
                        ImportRowStatus.INVALID,
                        exception.getMessage(),
                        row.values()));
            }
        }

        List<SalesReceiptRowValidationResult> validations = new ArrayList<>(failures);
        for (List<SalesReceiptRowMapper.SalesReceiptRowMapped> group : groups.values()) {
            SalesReceiptRowMapper.SalesReceiptRowMapped first = group.getFirst();
            List<String> groupErrors = new ArrayList<>();
            List<SalesReceiptLine> lines = new ArrayList<>();
            for (SalesReceiptRowMapper.SalesReceiptRowMapped current : group) {
                if (!Objects.equals(first.customer(), current.customer())
                        || !Objects.equals(first.txnDate(), current.txnDate())
                        || !Objects.equals(first.paymentMethod(), current.paymentMethod())
                        || !Objects.equals(first.depositAccount(), current.depositAccount())) {
                    groupErrors.add("Grouped rows for same receipt number must share customer/date/payment method/deposit account");
                    break;
                }
                lines.add(current.line());
            }
            NormalizedSalesReceipt receipt = new NormalizedSalesReceipt(
                    first.receiptNo(),
                    first.customer(),
                    first.txnDate(),
                    first.paymentMethod(),
                    first.depositAccount(),
                    lines);
            SalesReceiptRowValidationResult validation = validator.validate(first.rowNumber(), first.rawData(), receipt);
            if (!groupErrors.isEmpty()) {
                validation = new SalesReceiptRowValidationResult(
                        validation.rowNumber(),
                        validation.parsedRow(),
                        validation.salesReceipt(),
                        ImportRowStatus.INVALID,
                        validation.message() + "; " + String.join("; ", groupErrors),
                        validation.rawData());
            }
            validations.add(validation);
        }
        validations.sort(java.util.Comparator.comparingInt(SalesReceiptRowValidationResult::rowNumber));
        return validations;
    }

    private ImportRunEntity persistRun(String fileName,
                                       String mappingProfileName,
                                       SalesReceiptImportPreview preview,
                                       ImportRunStatus status,
                                       int importedRows) {
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.SALES_RECEIPT);
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
        run.setCompletedAt(Instant.now());
        preview.validations().forEach(validation -> run.getRowResults().add(buildRow(run, validation)));
        return importRunRepository.save(run);
    }

    private ImportRowResultEntity buildRow(ImportRunEntity run, SalesReceiptRowValidationResult validation) {
        ImportRowResultEntity row = new ImportRowResultEntity();
        row.setImportRun(run);
        row.setRowNumber(validation.rowNumber());
        row.setSourceIdentifier(validation.salesReceipt() == null ? null : validation.salesReceipt().receiptNo());
        row.setStatus(validation.status());
        row.setMessage(validation.message());
        row.setRawData(asJson(validation.rawData()));
        row.setNormalizedData(asJson(validation.salesReceipt()));
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
}
