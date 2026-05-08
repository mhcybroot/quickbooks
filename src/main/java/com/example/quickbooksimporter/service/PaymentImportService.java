package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.domain.PaymentImportPreview;
import com.example.quickbooksimporter.domain.PaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.PaymentRowValidationResult;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentImportService {

    private final InvoiceCsvParser parser;
    private final PaymentRowMapper rowMapper;
    private final PaymentImportValidator validator;
    private final ImportRunRepository importRunRepository;
    private final ObjectMapper objectMapper;
    private final QuickBooksConnectionService connectionService;
    private final QuickBooksGateway quickBooksGateway;

    public PaymentImportService(InvoiceCsvParser parser,
                                PaymentRowMapper rowMapper,
                                PaymentImportValidator validator,
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

    public PaymentImportPreview preview(String fileName, byte[] bytes, Map<NormalizedPaymentField, String> mapping) {
        return preview(fileName, bytes, mapping, Map.of(), DateFormatOption.AUTO);
    }

    public PaymentImportPreview preview(String fileName,
                                        byte[] bytes,
                                        Map<NormalizedPaymentField, String> mapping,
                                        Map<String, QuickBooksInvoiceRef> draftInvoiceRefs) {
        return preview(fileName, bytes, mapping, draftInvoiceRefs, DateFormatOption.AUTO);
    }

    public PaymentImportPreview preview(String fileName,
                                        byte[] bytes,
                                        Map<NormalizedPaymentField, String> mapping,
                                        Map<String, QuickBooksInvoiceRef> draftInvoiceRefs,
                                        DateFormatOption paymentDateFormat) {
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedPaymentField, String> finalMapping = new EnumMap<>(mapping);
        Map<String, BigDecimal> allocatedByInvoice = new HashMap<>();
        List<PaymentRowValidationResult> validations = document.rows().stream()
                .map(row -> validateRow(row, finalMapping, allocatedByInvoice, draftInvoiceRefs, paymentDateFormat))
                .toList();
        List<PaymentImportPreviewRow> rows = validations.stream()
                .map(result -> new PaymentImportPreviewRow(
                        result.rowNumber(),
                        result.payment() == null ? "" : result.payment().customer(),
                        result.payment() == null ? "" : result.payment().application().invoiceNo(),
                        result.payment() == null ? "" : result.payment().referenceNo(),
                        result.status(),
                        result.message()))
                .toList();
        return new PaymentImportPreview(fileName, finalMapping, document.headers(), rows, validations);
    }

    @Transactional
    public ImportExecutionResult execute(String fileName, String mappingProfileName, PaymentImportPreview preview) {
        return execute(fileName, mappingProfileName, preview, ImportExecutionOptions.standalone());
    }

    @Transactional
    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         PaymentImportPreview preview,
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
        run.setEntityType(EntityType.PAYMENT);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setExportCsv(null);
        applyExecutionOptions(run, options);

        for (PaymentRowValidationResult validation : preview.validations()) {
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
                NormalizedPayment payment = validation.payment();
                QuickBooksInvoiceRef invoiceRef = quickBooksGateway.findInvoiceByDocNumber(realmId, payment.application().invoiceNo());
                QuickBooksPaymentCreateResult created = quickBooksGateway.createPayment(realmId, payment, invoiceRef);
                rowEntity.setStatus(ImportRowStatus.IMPORTED);
                rowEntity.setCreatedEntityId(created.paymentId());
                String label = created.paymentNumber() == null ? payment.referenceNo() : created.paymentNumber();
                rowEntity.setMessage("Imported as QuickBooks payment " + label);
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
                ? "Imported " + imported + " payments."
                : "Imported " + imported + " ready payments; skipped " + skipped + " rows; " + failed + " failed during import. Check Import History for details.";
        return new ImportExecutionResult(saved, failed == 0, message);
    }

    private PaymentRowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                                   Map<NormalizedPaymentField, String> mapping,
                                                   Map<String, BigDecimal> allocatedByInvoice,
                                                   Map<String, QuickBooksInvoiceRef> draftInvoiceRefs,
                                                   DateFormatOption paymentDateFormat) {
        try {
            NormalizedPayment payment = rowMapper.map(row, mapping, paymentDateFormat);
            String invoiceNo = payment == null || payment.application() == null ? null : payment.application().invoiceNo();
            BigDecimal alreadyAllocated = invoiceNo == null ? BigDecimal.ZERO : allocatedByInvoice.getOrDefault(invoiceNo, BigDecimal.ZERO);
            PaymentRowValidationResult result = validator.validate(
                    row.rowNumber(),
                    row.values(),
                    payment,
                    alreadyAllocated,
                    invoiceNo == null ? null : draftInvoiceRefs.get(invoiceNo));
            if (result.status() == ImportRowStatus.READY && invoiceNo != null && payment.application().appliedAmount() != null) {
                allocatedByInvoice.merge(invoiceNo, payment.application().appliedAmount(), BigDecimal::add);
            }
            return result;
        } catch (Exception exception) {
            return new PaymentRowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, exception.getMessage(), row.values());
        }
    }

    private ImportRunEntity persistRun(String fileName,
                                       String mappingProfileName,
                                       PaymentImportPreview preview,
                                       ImportRunStatus status,
                                       int importedRows) {
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.PAYMENT);
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

    private ImportRowResultEntity buildRow(ImportRunEntity run, PaymentRowValidationResult validation) {
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
