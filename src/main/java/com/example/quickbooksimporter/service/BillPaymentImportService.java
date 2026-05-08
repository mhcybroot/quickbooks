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
import java.time.Instant;
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
        ParsedCsvDocument doc = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedBillPaymentField, String> finalMapping = new EnumMap<>(mapping);
        Map<String, BigDecimal> allocatedByBill = new HashMap<>();
        List<BillPaymentRowValidationResult> validations = doc.rows().stream()
                .map(r -> validateRow(r, finalMapping, allocatedByBill)).toList();
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
        if (preview.validations().stream().anyMatch(v -> v.status() != ImportRowStatus.READY)) {
            ImportRunEntity failed = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failed, false, "Import blocked because one or more rows are invalid.");
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        int imported = 0;
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.BILL_PAYMENT);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setExportCsv(null);
        applyExecutionOptions(run, options);
        for (BillPaymentRowValidationResult validation : preview.validations()) {
            ImportRowResultEntity row = buildRow(run, validation);
            try {
                NormalizedBillPayment payment = validation.payment();
                QuickBooksBillRef billRef = quickBooksGateway.findBillByDocNumber(realmId, payment.application().billNo());
                QuickBooksPaymentCreateResult created = quickBooksGateway.createBillPayment(realmId, payment, billRef);
                row.setStatus(ImportRowStatus.IMPORTED);
                row.setCreatedEntityId(created.paymentId());
                String label = created.paymentNumber() == null ? payment.referenceNo() : created.paymentNumber();
                row.setMessage("Imported as QuickBooks bill payment " + label);
                imported++;
            } catch (Exception ex) {
                row.setStatus(ImportRowStatus.FAILED);
                row.setMessage(ex.getMessage());
            }
            run.getRowResults().add(row);
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
        return new ImportExecutionResult(saved, failed == 0, failed == 0 ? "Imported " + imported + " bill payments." : "Imported " + imported + " bill payments, " + failed + " failed. Check Import History for row errors.");
    }

    private BillPaymentRowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                                       Map<NormalizedBillPaymentField, String> mapping,
                                                       Map<String, BigDecimal> allocatedByBill) {
        try {
            NormalizedBillPayment payment = rowMapper.map(row, mapping);
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
        run.setExportCsv(null);
        run.setCreatedAt(Instant.now());
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
}
