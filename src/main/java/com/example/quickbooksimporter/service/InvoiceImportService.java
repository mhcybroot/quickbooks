package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportPreview;
import com.example.quickbooksimporter.domain.ImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.domain.RowValidationResult;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedInvoiceField, String> finalMapping = new EnumMap<>(mapping);
        List<RowValidationResult> validations = document.rows().stream()
                .map(row -> validateRow(row, finalMapping))
                .toList();
        List<ImportPreviewRow> rows = validations.stream()
                .map(result -> new ImportPreviewRow(
                        result.rowNumber(),
                        result.invoice() == null ? "" : result.invoice().invoiceNo(),
                        result.invoice() == null ? "" : result.invoice().customer(),
                        result.invoice() == null || result.invoice().lines().isEmpty() ? "" : result.invoice().lines().getFirst().itemName(),
                        result.status(),
                        result.message()))
                .toList();
        String exportCsv = csvTemplateService.exportInvoices(validations.stream()
                .filter(result -> result.invoice() != null)
                .map(RowValidationResult::invoice)
                .toList());
        return new ImportPreview(fileName, finalMapping, document.headers(), rows, validations, exportCsv);
    }

    @Transactional
    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         ImportPreview preview) {
        return execute(fileName, mappingProfileName, preview, ImportExecutionOptions.standalone());
    }

    @Transactional
    public ImportExecutionResult execute(String fileName,
                                         String mappingProfileName,
                                         ImportPreview preview,
                                         ImportExecutionOptions options) {
        if (preview.validations().stream().anyMatch(result -> result.status() != ImportRowStatus.READY)) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failedRun, false, "Import blocked because one or more rows are invalid.");
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        String preflightError = preflightAutoCreateRequirements(realmId, preview.validations());
        if (preflightError != null) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failedRun, false, preflightError);
        }
        int imported = 0;
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.INVOICE);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setExportCsv(preview.exportCsv());
        applyExecutionOptions(run, options);

        for (RowValidationResult validation : preview.validations()) {
            ImportRowResultEntity rowEntity = buildRow(run, validation);
            try {
                NormalizedInvoice invoice = validation.invoice();
                invoice.lines().forEach(line -> quickBooksGateway.ensureServiceItem(realmId, line.itemName(), line.description()));
                quickBooksGateway.ensureCustomer(realmId, invoice.customer());
                QuickBooksInvoiceCreateResult created = quickBooksGateway.createInvoice(realmId, invoice);
                rowEntity.setStatus(ImportRowStatus.IMPORTED);
                rowEntity.setCreatedEntityId(created.invoiceId());
                rowEntity.setMessage("Imported as QuickBooks invoice " + created.docNumber());
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
                ? "Imported " + imported + " invoices."
                : "Imported " + imported + " invoices, " + failed + " failed. Check Import History for row errors.";
        return new ImportExecutionResult(saved, imported == preview.rows().size(), message);
    }

    public List<ImportRunEntity> recentRuns() {
        return importRunRepository.findTop20ByOrderByCreatedAtDesc();
    }

    public ImportRunEntity getRun(long id) {
        return importRunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Import run not found"));
    }

    private RowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                            Map<NormalizedInvoiceField, String> mapping) {
        try {
            return validator.validate(row.rowNumber(), row.values(), rowMapper.map(row, mapping));
        } catch (Exception exception) {
            return new RowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, exception.getMessage(), row.values());
        }
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
        run.setExportCsv(preview.exportCsv());
        run.setCreatedAt(Instant.now());
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
}
