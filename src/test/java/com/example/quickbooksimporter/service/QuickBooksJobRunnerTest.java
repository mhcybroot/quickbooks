package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportPreview;
import com.example.quickbooksimporter.domain.ImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuickBooksJobRunnerTest {

    @Test
    void previewRunsInsideCompanyContextAndCompletesJob() {
        CurrentCompanyService currentCompanyService = mock(CurrentCompanyService.class);
        AppJobService appJobService = mock(AppJobService.class);
        ImportWorkflowFacade workflowFacade = mock(ImportWorkflowFacade.class);
        InvoiceImportService invoiceImportService = mock(InvoiceImportService.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(currentCompanyService).runWithCompanyContext(eq(77L), any(Runnable.class));
        when(invoiceImportService.preview(eq("invoices.csv"), any(), any(), eq(false), eq(DateFormatOption.AUTO), any(PreviewProgressListener.class)))
                .thenReturn(new ImportPreview(
                        "invoices.csv",
                        Map.of(NormalizedInvoiceField.INVOICE_NO, "InvoiceNo"),
                        List.of("InvoiceNo"),
                        List.of(new ImportPreviewRow(1, "INV-1", "Acme", "Service", ImportRowStatus.READY, "")),
                        List.of(),
                        "csv",
                        false));

        QuickBooksJobRunner runner = new QuickBooksJobRunner(
                currentCompanyService,
                appJobService,
                workflowFacade,
                new ImportPreviewJobCodec(new ObjectMapper()),
                invoiceImportService,
                mock(PaymentImportService.class),
                mock(ExpenseImportService.class),
                mock(SalesReceiptImportService.class),
                mock(BillImportService.class),
                mock(BillPaymentImportService.class),
                mock(QuickBooksGateway.class),
                mock(QboCleanupService.class),
                mock(ReconciliationService.class),
                mock(QuickBooksConnectionService.class),
                mock(ImportBatchService.class));

        runner.runImportPreview(10L, 77L, EntityType.INVOICE,
                new QuickBooksJobService.ImportPreviewRequest(
                        "invoices.csv",
                        new byte[] {1, 2, 3},
                        DateFormatOption.AUTO,
                        false,
                        Map.of(NormalizedInvoiceField.INVOICE_NO, "InvoiceNo"),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()));

        verify(currentCompanyService).runWithCompanyContext(eq(77L), any(Runnable.class));
        verify(appJobService).markRunning(10L, 77L, "Running preview validation");
        verify(appJobService).completeSuccess(eq(10L), eq(77L), eq("Preview complete: 1 ready, 0 invalid."), any(ImportPreviewJobResult.class));
    }

    @Test
    void previewFailureMarksJobFailed() {
        CurrentCompanyService currentCompanyService = mock(CurrentCompanyService.class);
        AppJobService appJobService = mock(AppJobService.class);
        ImportWorkflowFacade workflowFacade = mock(ImportWorkflowFacade.class);
        InvoiceImportService invoiceImportService = mock(InvoiceImportService.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(currentCompanyService).runWithCompanyContext(eq(99L), any(Runnable.class));
        when(invoiceImportService.preview(eq("invoices.csv"), any(), any(), eq(false), eq(DateFormatOption.AUTO), any(PreviewProgressListener.class)))
                .thenThrow(new IllegalStateException("boom"));

        QuickBooksJobRunner runner = new QuickBooksJobRunner(
                currentCompanyService,
                appJobService,
                workflowFacade,
                new ImportPreviewJobCodec(new ObjectMapper()),
                invoiceImportService,
                mock(PaymentImportService.class),
                mock(ExpenseImportService.class),
                mock(SalesReceiptImportService.class),
                mock(BillImportService.class),
                mock(BillPaymentImportService.class),
                mock(QuickBooksGateway.class),
                mock(QboCleanupService.class),
                mock(ReconciliationService.class),
                mock(QuickBooksConnectionService.class),
                mock(ImportBatchService.class));

        runner.runImportPreview(11L, 99L, EntityType.INVOICE,
                new QuickBooksJobService.ImportPreviewRequest(
                        "invoices.csv",
                        new byte[] {1},
                        DateFormatOption.AUTO,
                        false,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()));

        verify(appJobService).completeFailure(11L, 99L, "boom");
    }
}
