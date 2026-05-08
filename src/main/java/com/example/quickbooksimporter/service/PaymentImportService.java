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
import java.time.Instant;
import java.util.EnumMap;
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
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedPaymentField, String> finalMapping = new EnumMap<>(mapping);
        List<PaymentRowValidationResult> validations = document.rows().stream()
                .map(row -> validateRow(row, finalMapping))
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
        if (preview.validations().stream().anyMatch(result -> result.status() != ImportRowStatus.READY)) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failedRun, false, "Import blocked because one or more rows are invalid.");
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        int imported = 0;
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
            try {
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
            }
            run.getRowResults().add(rowEntity);
        }
        run.setTotalRows(preview.rows().size());
        run.setValidRows(preview.rows().size());
        run.setInvalidRows(0);
        run.setDuplicateRows(0);
        run.setImportedRows(imported);
        run.setStatus(imported == preview.rows().size() ? ImportRunStatus.IMPORTED : ImportRunStatus.PARTIAL_FAILURE);
        run.setCompletedAt(Instant.now());
        ImportRunEntity saved = importRunRepository.save(run);
        int failed = preview.rows().size() - imported;
        String message = failed == 0
                ? "Imported " + imported + " payments."
                : "Imported " + imported + " payments, " + failed + " failed. Check Import History for row errors.";
        return new ImportExecutionResult(saved, imported == preview.rows().size(), message);
    }

    private PaymentRowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                                   Map<NormalizedPaymentField, String> mapping) {
        try {
            return validator.validate(row.rowNumber(), row.values(), rowMapper.map(row, mapping));
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
}
