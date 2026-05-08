package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportBatchRepository;
import com.example.quickbooksimporter.repository.ImportRunRepository;
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

    private ImportHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ImportHistoryService(importRunRepository, importBatchRepository);
    }

    @Test
    void filterRunsAppliesEntityStatusDateAndFileSearch() {
        ImportRunEntity invoice = run("invoice-may.csv", EntityType.INVOICE, ImportRunStatus.IMPORTED,
                LocalDate.of(2026, 5, 7).atStartOfDay().toInstant(ZoneOffset.UTC));
        ImportRunEntity payment = run("payments.csv", EntityType.PAYMENT, ImportRunStatus.PARTIAL_FAILURE,
                LocalDate.of(2026, 5, 5).atStartOfDay().toInstant(ZoneOffset.UTC));

        when(importRunRepository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(invoice, payment));

        List<ImportRunEntity> filtered = service.filterRuns(
                EntityType.INVOICE,
                ImportRunStatus.IMPORTED,
                LocalDate.of(2026, 5, 6),
                "invoice");

        assertEquals(1, filtered.size());
        assertEquals("invoice-may.csv", filtered.getFirst().getSourceFileName());
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
