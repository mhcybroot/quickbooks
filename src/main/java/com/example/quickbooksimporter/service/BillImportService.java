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
        ParsedCsvDocument doc = parser.parse(new ByteArrayInputStream(bytes));
        Map<NormalizedBillField, String> finalMapping = new EnumMap<>(mapping);
        List<BillRowValidationResult> validations = validateGrouped(doc, finalMapping);
        List<BillImportPreviewRow> rows = validations.stream().map(v -> new BillImportPreviewRow(
                v.rowNumber(),
                v.bill() == null ? "" : v.bill().billNo(),
                v.bill() == null ? "" : v.bill().vendor(),
                v.bill() == null ? 0 : v.bill().lines().size(),
                v.status(),
                v.message())).toList();
        return new BillImportPreview(fileName, finalMapping, doc.headers(), rows, validations);
    }

    @Transactional
    public ImportExecutionResult execute(String fileName, String mappingProfileName, BillImportPreview preview) {
        if (preview.validations().stream().anyMatch(v -> v.status() != ImportRowStatus.READY)) {
            ImportRunEntity failed = persistRun(fileName, mappingProfileName, preview, ImportRunStatus.VALIDATION_FAILED, 0);
            return new ImportExecutionResult(failed, false, "Import blocked because one or more rows are invalid.");
        }
        String realmId = connectionService.getActiveConnection().getRealmId();
        int imported = 0;
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(EntityType.BILL);
        run.setStatus(ImportRunStatus.PREVIEW_READY);
        run.setSourceFileName(fileName);
        run.setMappingProfileName(mappingProfileName);
        run.setCreatedAt(Instant.now());
        run.setExportCsv(null);
        for (BillRowValidationResult validation : preview.validations()) {
            ImportRowResultEntity row = buildRow(run, validation);
            try {
                NormalizedBill bill = validation.bill();
                quickBooksGateway.ensureVendor(realmId, bill.vendor());
                for (BillLine line : bill.lines()) {
                    if (line.category() != null) {
                        quickBooksGateway.ensureExpenseCategory(realmId, line.category());
                    }
                }
                QuickBooksBillCreateResult created = quickBooksGateway.createBill(realmId, bill);
                row.setStatus(ImportRowStatus.IMPORTED);
                row.setCreatedEntityId(created.billId());
                row.setMessage("Imported as QuickBooks bill " + (created.docNumber() == null ? bill.billNo() : created.docNumber()));
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
        run.setStatus(imported == preview.rows().size() ? ImportRunStatus.IMPORTED : ImportRunStatus.VALIDATION_FAILED);
        run.setCompletedAt(Instant.now());
        ImportRunEntity saved = importRunRepository.save(run);
        int failed = preview.rows().size() - imported;
        return new ImportExecutionResult(saved, failed == 0, failed == 0 ? "Imported " + imported + " bills." : "Imported " + imported + " bills, " + failed + " failed. Check Import History for row errors.");
    }

    private List<BillRowValidationResult> validateGrouped(ParsedCsvDocument doc, Map<NormalizedBillField, String> mapping) {
        Map<String, List<BillRowMapper.BillRowMapped>> groups = new HashMap<>();
        List<BillRowValidationResult> validations = new ArrayList<>();
        for (var row : doc.rows()) {
            try {
                var mapped = rowMapper.map(row, mapping);
                String key = mapped.billNo() == null ? "ROW-" + mapped.rowNumber() : mapped.billNo();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(mapped);
            } catch (Exception ex) {
                validations.add(new BillRowValidationResult(row.rowNumber(), row, null, ImportRowStatus.INVALID, ex.getMessage(), row.values()));
            }
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
            BillRowValidationResult v = validator.validate(first.rowNumber(), first.rawData(), bill);
            if (!gErrors.isEmpty()) {
                v = new BillRowValidationResult(v.rowNumber(), v.parsedRow(), v.bill(), ImportRowStatus.INVALID, v.message() + "; " + String.join("; ", gErrors), v.rawData());
            }
            validations.add(v);
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
        run.setExportCsv(null);
        run.setCreatedAt(Instant.now());
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
}
