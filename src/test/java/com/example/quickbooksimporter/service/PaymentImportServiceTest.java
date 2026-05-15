package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedPayment;
import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.domain.PaymentApplication;
import com.example.quickbooksimporter.domain.PaymentImportPreview;
import com.example.quickbooksimporter.domain.PaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.PaymentRowValidationResult;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentImportServiceTest {

    @Mock
    private PaymentImportValidator validator;

    @Mock
    private ImportRunRepository importRunRepository;

    @Mock
    private QuickBooksConnectionService connectionService;

    @Mock
    private QuickBooksGateway quickBooksGateway;

    private PaymentImportService service;

    @BeforeEach
    void setUp() {
        service = new PaymentImportService(
                new InvoiceCsvParser(),
                new PaymentRowMapper(),
                validator,
                importRunRepository,
                new ObjectMapper().findAndRegisterModules(),
                connectionService,
                quickBooksGateway);
        when(importRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyEntity company = new CompanyEntity();
        when(connectionService.requireCurrentCompany()).thenReturn(company);
    }

    @Test
    void executeResolvesInvoicesThenUsesBatchPaymentCreate() {
        NormalizedPayment payment = new NormalizedPayment(
                "Acme",
                java.time.LocalDate.of(2026, 5, 1),
                "REF-100",
                "Bank Transfer",
                "Checking",
                new BigDecimal("100.00"),
                new PaymentApplication("INV-100", new BigDecimal("100.00")));
        PaymentImportPreview preview = new PaymentImportPreview(
                "payments.csv",
                mapping(),
                List.of(),
                List.of(new PaymentImportPreviewRow(1, "Acme", "INV-100", "REF-100", ImportRowStatus.READY, "")),
                List.of(new PaymentRowValidationResult(1, new com.example.quickbooksimporter.domain.ParsedCsvRow(1, Map.of()), payment, ImportRowStatus.READY, "", Map.of())));
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        when(connectionService.getActiveConnection()).thenReturn(connection);
        when(quickBooksGateway.findInvoiceByDocNumber("realm-1", "INV-100"))
                .thenReturn(new QuickBooksInvoiceRef("invoice-1", "INV-100", "cust-1", "Acme", new java.math.BigDecimal("100.00")));
        when(quickBooksGateway.createPaymentsBatch(eq("realm-1"), anyList())).thenReturn(List.of(
                new QuickBooksBatchCreateResult(true, "payment-1", "PAY-100", null, null)));

        ImportExecutionResult result = service.execute("payments.csv", "default", preview);

        assertThat(result.success()).isTrue();
        assertThat(result.importRun().getStatus()).isEqualTo(ImportRunStatus.IMPORTED);
        assertThat(result.importRun().getAttemptedRows()).isEqualTo(1);
        assertThat(result.importRun().getImportedRows()).isEqualTo(1);
        verify(quickBooksGateway).findInvoiceByDocNumber("realm-1", "INV-100");
        verify(quickBooksGateway).createPaymentsBatch(eq("realm-1"), anyList());
    }

    @Test
    void executeReadyOnlyPersistsIntermediateProgressAfterReadyRowsStart() {
        List<RunSaveSnapshot> saveSnapshots = new ArrayList<>();
        when(importRunRepository.save(any())).thenAnswer(invocation -> {
            var run = invocation.<com.example.quickbooksimporter.persistence.ImportRunEntity>getArgument(0);
            saveSnapshots.add(new RunSaveSnapshot(
                    run.getAttemptedRows(),
                    run.getSkippedRows(),
                    run.getImportedRows(),
                    run.getCompletedAt()));
            return run;
        });
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        when(connectionService.getActiveConnection()).thenReturn(connection);
        when(quickBooksGateway.findInvoiceByDocNumber("realm-1", "INV-100"))
                .thenReturn(new QuickBooksInvoiceRef("invoice-1", "INV-100", "cust-1", "Acme", new java.math.BigDecimal("100.00")));
        when(quickBooksGateway.findInvoiceByDocNumber("realm-1", "INV-101"))
                .thenReturn(new QuickBooksInvoiceRef("invoice-2", "INV-101", "cust-1", "Acme", new java.math.BigDecimal("50.00")));
        when(quickBooksGateway.createPaymentsBatch(eq("realm-1"), anyList())).thenReturn(List.of(
                new QuickBooksBatchCreateResult(true, "payment-1", "PAY-100", null, null),
                new QuickBooksBatchCreateResult(true, "payment-2", "PAY-101", null, null)));
        PaymentImportPreview preview = new PaymentImportPreview(
                "payments.csv",
                mapping(),
                List.of(),
                List.of(
                        new PaymentImportPreviewRow(1, "Acme", "INV-SKIP-1", "REF-SKIP-1", ImportRowStatus.INVALID, "Invalid"),
                        new PaymentImportPreviewRow(2, "Acme", "INV-SKIP-2", "REF-SKIP-2", ImportRowStatus.INVALID, "Invalid"),
                        new PaymentImportPreviewRow(3, "Acme", "INV-SKIP-3", "REF-SKIP-3", ImportRowStatus.INVALID, "Invalid"),
                        new PaymentImportPreviewRow(4, "Acme", "INV-SKIP-4", "REF-SKIP-4", ImportRowStatus.INVALID, "Invalid"),
                        new PaymentImportPreviewRow(5, "Acme", "INV-100", "REF-100", ImportRowStatus.READY, ""),
                        new PaymentImportPreviewRow(6, "Acme", "INV-101", "REF-101", ImportRowStatus.READY, "")),
                List.of(
                        new PaymentRowValidationResult(1, new com.example.quickbooksimporter.domain.ParsedCsvRow(1, Map.of()),
                                new NormalizedPayment("Acme", java.time.LocalDate.of(2026, 5, 1), "REF-SKIP-1", "Bank Transfer", "Checking",
                                        BigDecimal.TEN, new PaymentApplication("INV-SKIP-1", BigDecimal.TEN)),
                                ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new PaymentRowValidationResult(2, new com.example.quickbooksimporter.domain.ParsedCsvRow(2, Map.of()),
                                new NormalizedPayment("Acme", java.time.LocalDate.of(2026, 5, 1), "REF-SKIP-2", "Bank Transfer", "Checking",
                                        BigDecimal.TEN, new PaymentApplication("INV-SKIP-2", BigDecimal.TEN)),
                                ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new PaymentRowValidationResult(3, new com.example.quickbooksimporter.domain.ParsedCsvRow(3, Map.of()),
                                new NormalizedPayment("Acme", java.time.LocalDate.of(2026, 5, 1), "REF-SKIP-3", "Bank Transfer", "Checking",
                                        BigDecimal.TEN, new PaymentApplication("INV-SKIP-3", BigDecimal.TEN)),
                                ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new PaymentRowValidationResult(4, new com.example.quickbooksimporter.domain.ParsedCsvRow(4, Map.of()),
                                new NormalizedPayment("Acme", java.time.LocalDate.of(2026, 5, 1), "REF-SKIP-4", "Bank Transfer", "Checking",
                                        BigDecimal.TEN, new PaymentApplication("INV-SKIP-4", BigDecimal.TEN)),
                                ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new PaymentRowValidationResult(5, new com.example.quickbooksimporter.domain.ParsedCsvRow(5, Map.of()),
                                new NormalizedPayment("Acme", java.time.LocalDate.of(2026, 5, 1), "REF-100", "Bank Transfer", "Checking",
                                        new BigDecimal("100.00"), new PaymentApplication("INV-100", new BigDecimal("100.00"))),
                                ImportRowStatus.READY, "", Map.of()),
                        new PaymentRowValidationResult(6, new com.example.quickbooksimporter.domain.ParsedCsvRow(6, Map.of()),
                                new NormalizedPayment("Acme", java.time.LocalDate.of(2026, 5, 1), "REF-101", "Bank Transfer", "Checking",
                                        new BigDecimal("50.00"), new PaymentApplication("INV-101", new BigDecimal("50.00"))),
                                ImportRowStatus.READY, "", Map.of())));

        ImportExecutionResult result = service.execute(
                "payments.csv",
                "default",
                preview,
                new ImportExecutionOptions(null, null, null, ImportExecutionMode.IMPORT_READY_ONLY));

        assertThat(result.success()).isTrue();
        assertThat(result.importRun().getStatus()).isEqualTo(ImportRunStatus.PARTIAL_FAILURE);
        assertThat(result.importRun().getAttemptedRows()).isEqualTo(2);
        assertThat(result.importRun().getSkippedRows()).isEqualTo(4);
        assertThat(saveSnapshots).anySatisfy(snapshot -> {
            assertThat(snapshot.attemptedRows()).isEqualTo(2);
            assertThat(snapshot.skippedRows()).isEqualTo(4);
            assertThat(snapshot.importedRows()).isEqualTo(1);
            assertThat(snapshot.completedAt()).isNull();
        });
    }

    private Map<NormalizedPaymentField, String> mapping() {
        Map<NormalizedPaymentField, String> mapping = new EnumMap<>(NormalizedPaymentField.class);
        Arrays.stream(NormalizedPaymentField.values()).forEach(field -> mapping.put(field, field.sampleHeader()));
        return mapping;
    }

    private record RunSaveSnapshot(int attemptedRows, int skippedRows, int importedRows, Instant completedAt) {
    }
}
