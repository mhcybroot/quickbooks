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
import java.time.Instant;
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
        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedExpenseField, String> finalMapping = new EnumMap<>(mapping);
        List<ExpenseRowValidationResult> validations = document.rows().stream()
                .map(row -> validateRow(row, finalMapping))
                .toList();
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

    @Transactional
    public ImportExecutionResult execute(String fileName, String mappingProfileName, ExpenseImportPreview preview) {
        if (preview.validations().stream().anyMatch(result -> result.status() != ImportRowStatus.READY)) {
            ImportRunEntity failedRun = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failedRun, false, "Import blocked because one or more rows are invalid.");
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        int imported = 0;
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.EXPENSE);
        run.setStatus(ImportRunStatus.PREVIEW_READY);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setExportCsv(null);

        for (ExpenseRowValidationResult validation : preview.validations()) {
            ImportRowResultEntity rowEntity = buildRow(run, validation);
            try {
                NormalizedExpense expense = validation.expense();
                quickBooksGateway.ensureVendor(realmId, expense.vendor());
                quickBooksGateway.ensureExpenseCategory(realmId, expense.category());
                QuickBooksExpenseCreateResult created = quickBooksGateway.createExpense(realmId, expense);
                rowEntity.setStatus(ImportRowStatus.IMPORTED);
                rowEntity.setCreatedEntityId(created.expenseId());
                String label = created.expenseNumber() == null ? expense.referenceNo() : created.expenseNumber();
                rowEntity.setMessage("Imported as QuickBooks expense " + label);
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
        run.setStatus(imported == preview.rows().size() ? ImportRunStatus.IMPORTED : ImportRunStatus.VALIDATION_FAILED);
        run.setCompletedAt(Instant.now());
        ImportRunEntity saved = importRunRepository.save(run);
        int failed = preview.rows().size() - imported;
        String message = failed == 0
                ? "Imported " + imported + " expenses."
                : "Imported " + imported + " expenses, " + failed + " failed. Check Import History for row errors.";
        return new ImportExecutionResult(saved, imported == preview.rows().size(), message);
    }

    private ExpenseRowValidationResult validateRow(com.example.quickbooksimporter.domain.ParsedCsvRow row,
                                                   Map<NormalizedExpenseField, String> mapping) {
        try {
            return validator.validate(row.rowNumber(), row.values(), rowMapper.map(row, mapping));
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
        run.setExportCsv(null);
        run.setCreatedAt(Instant.now());
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
}
