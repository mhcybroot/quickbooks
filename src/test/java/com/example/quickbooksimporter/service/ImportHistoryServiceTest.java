package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportBatchRepository;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportHistoryServiceTest {

    @Mock
    private ImportRunRepository importRunRepository;

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private CurrentCompanyService currentCompanyService;

    private ImportHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ImportHistoryService(importRunRepository, importBatchRepository, currentCompanyService);
        when(currentCompanyService.requireCurrentCompanyId()).thenReturn(1L);
    }

    @Test
    void filterRunsAppliesEntityStatusDateAndFileSearch() {
        ImportRunEntity invoice = run("invoice-may.csv", EntityType.INVOICE, ImportRunStatus.IMPORTED,
                LocalDate.of(2026, 5, 7).atStartOfDay().toInstant(ZoneOffset.UTC));
        ImportRunEntity payment = run("payments.csv", EntityType.PAYMENT, ImportRunStatus.PARTIAL_FAILURE,
                LocalDate.of(2026, 5, 5).atStartOfDay().toInstant(ZoneOffset.UTC));

        when(importRunRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(invoice, payment));

        List<ImportRunEntity> filtered = service.filterRuns(
                EntityType.INVOICE,
                ImportRunStatus.IMPORTED,
                LocalDate.of(2026, 5, 6),
                "invoice");

        assertEquals(1, filtered.size());
        assertEquals("invoice-may.csv", filtered.getFirst().getSourceFileName());
    }

    @Test
    void buildRunExportCsvIncludesMetadataAndRowDataWithEscaping() {
        ImportRunEntity run = run("invoice.csv", EntityType.INVOICE, ImportRunStatus.PARTIAL_FAILURE,
                LocalDate.of(2026, 5, 7).atStartOfDay().toInstant(ZoneOffset.UTC));
        run.setTotalRows(3);
        run.setValidRows(2);
        run.setInvalidRows(1);
        run.setDuplicateRows(0);
        run.setAttemptedRows(2);
        run.setSkippedRows(1);
        run.setImportedRows(1);

        ImportRowResultEntity row = new ImportRowResultEntity();
        row.setRowNumber(2);
        row.setSourceIdentifier("INV-1002");
        row.setStatus(ImportRowStatus.INVALID);
        row.setMessage("Bad amount, line 2");
        row.setCreatedEntityId("123");
        row.setRawData("{\"a\":1,\n\"b\":2}");
        row.setNormalizedData("{\"x\":\"y\"}");
        run.getRowResults().add(row);

        String csv = service.buildRunExportCsv(run);

        assertTrue(csv.contains("runId,entityType,status,sourceFileName"));
        assertTrue(csv.contains("invoice.csv"));
        assertTrue(csv.contains("PARTIAL_FAILURE"));
        assertTrue(csv.contains("INV-1002"));
        assertTrue(csv.contains("\"Bad amount, line 2\""));
        assertTrue(csv.contains("\"{\"\"a\"\":1,"));
    }

    @Test
    void buildRunExportCsvEmitsMetadataRowWhenNoRowResults() {
        ImportRunEntity run = run("payments.csv", EntityType.PAYMENT, ImportRunStatus.IMPORTED,
                LocalDate.of(2026, 5, 8).atStartOfDay().toInstant(ZoneOffset.UTC));
        run.setTotalRows(2);
        run.setImportedRows(2);

        String csv = service.buildRunExportCsv(run);
        String[] lines = csv.split("\\R");

        assertEquals(2, lines.length);
        assertTrue(lines[1].contains("payments.csv"));
        assertTrue(lines[1].contains("IMPORTED"));
    }

    @Test
    void runExportFileNameUsesRunIdAndEntityType() throws Exception {
        ImportRunEntity run = run("expenses.csv", EntityType.EXPENSE, ImportRunStatus.IMPORTED, Instant.now());
        Field idField = ImportRunEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(run, 44L);

        String fileName = service.runExportFileName(run);

        assertEquals("import-run-44-expense.csv", fileName);
    }

    private ImportRunEntity run(String sourceFile, EntityType entityType, ImportRunStatus status, Instant createdAt) {
        ImportRunEntity run = new ImportRunEntity();
        run.setSourceFileName(sourceFile);
        run.setEntityType(entityType);
        run.setStatus(status);
        run.setCreatedAt(createdAt);
        return run;
    }
}
