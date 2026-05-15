package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportBatchStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportBatchRepository;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportBatchServiceTest {

    @Mock
    private ImportBatchRepository importBatchRepository;

    @Mock
    private ImportRunRepository importRunRepository;

    @Mock
    private ImportWorkflowFacade workflowFacade;

    @Mock
    private CurrentCompanyService currentCompanyService;

    private ImportBatchService service;

    @BeforeEach
    void setUp() {
        service = new ImportBatchService(importBatchRepository, importRunRepository, workflowFacade, currentCompanyService);
        when(currentCompanyService.requireCurrentCompanyId()).thenReturn(1L);
    }

    @Test
    void dependencyWarningsFlagsMissingInvoiceReferences() {
        Object preview = new Object();
        ImportPreviewSummary summary = new ImportPreviewSummary(
                EntityType.PAYMENT, "payments.csv", List.of(), 1, 1, 0, 0, null, null, List.of(), preview);

        when(importRunRepository.findTop100ByCompanyIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(workflowFacade.producedIdentifiers(EntityType.PAYMENT, preview)).thenReturn(Set.of());
        when(workflowFacade.requiredParentIdentifiers(EntityType.PAYMENT, preview)).thenReturn(Set.of("INV-100"));

        List<String> warnings = service.dependencyWarnings(List.of(
                new ImportBatchService.BatchFileRequest(1, EntityType.PAYMENT, "payments.csv", null, summary, false)));

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("INV-100"));
    }

    @Test
    void executeBatchRunsInvoicesBeforePayments() {
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setBatchName("Ops batch");
        batch.setStatus(ImportBatchStatus.VALIDATED);
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());

        Object invoicePreview = new Object();
        Object paymentPreview = new Object();
        ImportPreviewSummary invoiceSummary = new ImportPreviewSummary(
                EntityType.INVOICE, "invoice.csv", List.of(), 2, 2, 0, 0, "csv", null, List.of(), invoicePreview);
        ImportPreviewSummary paymentSummary = new ImportPreviewSummary(
                EntityType.PAYMENT, "payment.csv", List.of(), 2, 2, 0, 0, null, null, List.of(), paymentPreview);

        when(importBatchRepository.findByIdAndCompanyId(7L, 1L)).thenReturn(Optional.of(batch));
        when(importBatchRepository.save(any(ImportBatchEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowFacade.execute(eq(EntityType.INVOICE), eq("invoice.csv"), any(), eq(invoicePreview), any()))
                .thenReturn(new ImportExecutionResult(run("invoice.csv", EntityType.INVOICE), true, "ok"));
        when(workflowFacade.execute(eq(EntityType.PAYMENT), eq("payment.csv"), any(), eq(paymentPreview), any()))
                .thenReturn(new ImportExecutionResult(run("payment.csv", EntityType.PAYMENT), true, "ok"));

        service.executeBatch(7L, List.of(
                new ImportBatchService.BatchFileRequest(1, EntityType.PAYMENT, "payment.csv", "pay-profile", paymentSummary, false),
                new ImportBatchService.BatchFileRequest(2, EntityType.INVOICE, "invoice.csv", "inv-profile", invoiceSummary, false)));

        InOrder inOrder = inOrder(workflowFacade);
        inOrder.verify(workflowFacade).execute(eq(EntityType.INVOICE), eq("invoice.csv"), eq("inv-profile"), eq(invoicePreview), any());
        inOrder.verify(workflowFacade).execute(eq(EntityType.PAYMENT), eq("payment.csv"), eq("pay-profile"), eq(paymentPreview), any());
    }

    @Test
    void prepareBatchExecutionPersistsPlannedRowCounts() {
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setBatchName("Ops batch");
        batch.setStatus(ImportBatchStatus.VALIDATED);
        batch.setCreatedAt(Instant.now());
        batch.setUpdatedAt(Instant.now());
        Object invoicePreview = new Object();
        Object paymentPreview = new Object();
        ImportPreviewSummary invoiceSummary = new ImportPreviewSummary(
                EntityType.INVOICE, "invoice.csv", List.of(), 10, 10, 0, 0, "csv", null, List.of(), invoicePreview);
        ImportPreviewSummary paymentSummary = new ImportPreviewSummary(
                EntityType.PAYMENT, "payment.csv", List.of(), 8, 5, 3, 0, null, null, List.of(), paymentPreview);

        when(importBatchRepository.findByIdAndCompanyId(9L, 1L)).thenReturn(Optional.of(batch));
        when(importBatchRepository.save(any(ImportBatchEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ImportBatchEntity prepared = service.prepareBatchExecution(9L, List.of(
                new ImportBatchService.BatchFileRequest(1, EntityType.INVOICE, "invoice.csv", "inv-profile", invoiceSummary, false),
                new ImportBatchService.BatchFileRequest(2, EntityType.PAYMENT, "payment.csv", "pay-profile", paymentSummary, true)));

        assertEquals(18, prepared.getPlannedTotalRows());
        assertEquals(15, prepared.getPlannedRunnableRows());
        assertEquals(2, prepared.getRunnableFiles());
        assertEquals(0, prepared.getCompletedFiles());
        assertEquals(ImportBatchStatus.RUNNING, prepared.getStatus());
        assertTrue(prepared.getStartedAt() != null);
    }

    private ImportRunEntity run(String fileName, EntityType entityType) {
        ImportRunEntity run = new ImportRunEntity();
        run.setSourceFileName(fileName);
        run.setEntityType(entityType);
        run.setStatus(ImportRunStatus.IMPORTED);
        run.setCreatedAt(Instant.now());
        return run;
    }
}
