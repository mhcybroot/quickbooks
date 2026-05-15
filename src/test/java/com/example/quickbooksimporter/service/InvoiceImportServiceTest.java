package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.domain.RowValidationResult;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
class InvoiceImportServiceTest {

    @Mock
    private InvoiceImportValidator validator;

    @Mock
    private ImportRunRepository importRunRepository;

    @Mock
    private QuickBooksConnectionService connectionService;

    @Mock
    private QuickBooksGateway quickBooksGateway;

    private InvoiceImportService service;
    private QuickBooksProperties quickBooksProperties;

    @BeforeEach
    void setUp() {
        quickBooksProperties = mock(QuickBooksProperties.class);
        service = new InvoiceImportService(
                new InvoiceCsvParser(),
                new InvoiceRowMapper(),
                validator,
                new CsvTemplateService(),
                importRunRepository,
                new ObjectMapper().findAndRegisterModules(),
                connectionService,
                quickBooksGateway,
                quickBooksProperties);
        lenient().when(validator.validate(anyInt(), anyMap(), any(NormalizedInvoice.class)))
                .thenAnswer(invocation -> new RowValidationResult(
                        invocation.getArgument(0),
                        new com.example.quickbooksimporter.domain.ParsedCsvRow(invocation.getArgument(0), invocation.getArgument(1)),
                        invocation.getArgument(2),
                        ImportRowStatus.READY,
                        "",
                        invocation.getArgument(1)));
        lenient().when(importRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyEntity company = new CompanyEntity();
        lenient().when(connectionService.requireCurrentCompany()).thenReturn(company);
        lenient().when(quickBooksProperties.serviceItemIncomeAccountId()).thenReturn("income-1");
    }

    @Test
    void keepsRepeatedInvoiceNumbersSeparateWhenGroupingIsDisabled() {
        var preview = service.preview("invoice.csv", sampleCsv().getBytes(StandardCharsets.UTF_8), mapping(), false);

        assertThat(preview.groupingEnabled()).isFalse();
        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.validations()).hasSize(2);
        assertThat(preview.validations())
                .extracting(validation -> validation.invoice().lines().size())
                .containsExactly(1, 1);
    }

    @Test
    void mergesRepeatedInvoiceNumbersIntoOneInvoiceWhenGroupingIsEnabled() {
        var preview = service.preview("invoice.csv", sampleCsv().getBytes(StandardCharsets.UTF_8), mapping(), true);

        assertThat(preview.groupingEnabled()).isTrue();
        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.validations()).hasSize(1);
        assertThat(preview.validations().getFirst().invoice().lines()).hasSize(2);
        assertThat(preview.exportCsv()).contains("Consulting").contains("Support");
    }

    @Test
    void flagsGroupedRowsWhenInvoiceLevelFieldsConflict() {
        String csv = String.join("\n",
                String.join(",", headers()),
                "INV-100,Acme,2026-05-01,2026-05-31,Net 30,HQ,Original memo,Consulting,Implementation,1,100,100,N,,2026-05-01",
                "INV-100,Acme,2026-05-01,2026-05-31,Net 30,HQ,Different memo,Support,Monthly support,1,25,25,N,,2026-05-02");

        var preview = service.preview("invoice.csv", csv.getBytes(StandardCharsets.UTF_8), mapping(), true);

        assertThat(preview.rows()).hasSize(1);
        assertThat(preview.validations().getFirst().status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(preview.validations().getFirst().message())
                .contains("Grouped rows for the same invoice must share customer, invoice date, due date, terms, location, and memo");
    }

    @Test
    void executeImportsReadyInvoicesViaBatchGateway() {
        var preview = service.preview("invoice.csv", sampleCsv().getBytes(StandardCharsets.UTF_8), mapping(), true);
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        when(connectionService.getActiveConnection()).thenReturn(connection);
        when(quickBooksGateway.createInvoicesBatch(eq("realm-1"), anyList())).thenReturn(List.of(
                new QuickBooksBatchCreateResult(true, "inv-1", "INV-100", null, null)));

        ImportExecutionResult result = service.execute("invoice.csv", "default", preview);

        assertThat(result.success()).isTrue();
        assertThat(result.importRun().getStatus()).isEqualTo(ImportRunStatus.IMPORTED);
        assertThat(result.importRun().getImportedRows()).isEqualTo(1);
        assertThat(result.importRun().getAttemptedRows()).isEqualTo(1);
        assertThat(result.importRun().getRowResults()).hasSize(1);
        assertThat(result.importRun().getRowResults().getFirst().getStatus()).isEqualTo(ImportRowStatus.IMPORTED);
        verify(quickBooksGateway).createInvoicesBatch(eq("realm-1"), anyList());
    }

    @Test
    void executeRecordsPartialFailuresReturnedFromBatchGateway() {
        var preview = service.preview("invoice.csv", sampleCsv().getBytes(StandardCharsets.UTF_8), mapping(), true);
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("realm-1");
        when(connectionService.getActiveConnection()).thenReturn(connection);
        when(quickBooksGateway.createInvoicesBatch(eq("realm-1"), anyList())).thenReturn(List.of(
                new QuickBooksBatchCreateResult(false, null, null, "Duplicate invoice", "tid-1")));

        ImportExecutionResult result = service.execute("invoice.csv", "default", preview);

        assertThat(result.success()).isFalse();
        assertThat(result.importRun().getStatus()).isEqualTo(ImportRunStatus.PARTIAL_FAILURE);
        assertThat(result.importRun().getImportedRows()).isZero();
        assertThat(result.importRun().getAttemptedRows()).isEqualTo(1);
        assertThat(result.importRun().getRowResults().getFirst().getStatus()).isEqualTo(ImportRowStatus.FAILED);
        assertThat(result.importRun().getRowResults().getFirst().getMessage()).isEqualTo("Duplicate invoice");
    }

    @Test
    void executeReadyOnlyPersistsIntermediateProgressForGroupedInvoices() {
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
        when(quickBooksGateway.createInvoicesBatch(eq("realm-1"), anyList())).thenReturn(List.of(
                new QuickBooksBatchCreateResult(true, "inv-1", "INV-READY-1", null, null),
                new QuickBooksBatchCreateResult(true, "inv-2", "INV-READY-2", null, null)));
        NormalizedInvoice readyInvoice1 = new NormalizedInvoice(
                "INV-READY-1",
                "Acme",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "Net 30",
                "HQ",
                "Memo",
                List.of(new com.example.quickbooksimporter.domain.InvoiceLine(
                        "Consulting",
                        "Implementation",
                        BigDecimal.ONE,
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00"),
                        false,
                        null,
                        LocalDate.of(2026, 5, 1))));
        NormalizedInvoice readyInvoice2 = new NormalizedInvoice(
                "INV-READY-2",
                "Acme",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "Net 30",
                "HQ",
                "Memo",
                List.of(new com.example.quickbooksimporter.domain.InvoiceLine(
                        "Support",
                        "Monthly support",
                        BigDecimal.ONE,
                        new BigDecimal("50.00"),
                        new BigDecimal("50.00"),
                        false,
                        null,
                        LocalDate.of(2026, 5, 1))));
        com.example.quickbooksimporter.domain.ImportPreview preview = new com.example.quickbooksimporter.domain.ImportPreview(
                "invoice.csv",
                mapping(),
                List.of(),
                List.of(
                        new com.example.quickbooksimporter.domain.ImportPreviewRow(1, "INV-SKIP-1", "Acme", "Service", ImportRowStatus.INVALID, "Invalid"),
                        new com.example.quickbooksimporter.domain.ImportPreviewRow(2, "INV-SKIP-2", "Acme", "Service", ImportRowStatus.INVALID, "Invalid"),
                        new com.example.quickbooksimporter.domain.ImportPreviewRow(3, "INV-SKIP-3", "Acme", "Service", ImportRowStatus.INVALID, "Invalid"),
                        new com.example.quickbooksimporter.domain.ImportPreviewRow(4, "INV-SKIP-4", "Acme", "Service", ImportRowStatus.INVALID, "Invalid"),
                        new com.example.quickbooksimporter.domain.ImportPreviewRow(5, "INV-READY-1", "Acme", "Service", ImportRowStatus.READY, ""),
                        new com.example.quickbooksimporter.domain.ImportPreviewRow(6, "INV-READY-2", "Acme", "Service", ImportRowStatus.READY, "")),
                List.of(
                        new RowValidationResult(1, new com.example.quickbooksimporter.domain.ParsedCsvRow(1, Map.of()), readyInvoice1, ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new RowValidationResult(2, new com.example.quickbooksimporter.domain.ParsedCsvRow(2, Map.of()), readyInvoice1, ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new RowValidationResult(3, new com.example.quickbooksimporter.domain.ParsedCsvRow(3, Map.of()), readyInvoice1, ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new RowValidationResult(4, new com.example.quickbooksimporter.domain.ParsedCsvRow(4, Map.of()), readyInvoice1, ImportRowStatus.INVALID, "Invalid", Map.of()),
                        new RowValidationResult(5, new com.example.quickbooksimporter.domain.ParsedCsvRow(5, Map.of()), readyInvoice1, ImportRowStatus.READY, "", Map.of()),
                        new RowValidationResult(6, new com.example.quickbooksimporter.domain.ParsedCsvRow(6, Map.of()), readyInvoice2, ImportRowStatus.READY, "", Map.of())),
                null,
                true);

        ImportExecutionResult result = service.execute(
                "invoice.csv",
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

    private Map<NormalizedInvoiceField, String> mapping() {
        Map<NormalizedInvoiceField, String> mapping = new EnumMap<>(NormalizedInvoiceField.class);
        Arrays.stream(NormalizedInvoiceField.values()).forEach(field -> mapping.put(field, field.sampleHeader()));
        return mapping;
    }

    private String sampleCsv() {
        return String.join("\n",
                String.join(",", headers()),
                "INV-100,Acme,2026-05-01,2026-05-31,Net 30,HQ,Grouped memo,Consulting,Implementation,1,100,100,N,,2026-05-01",
                "INV-100,Acme,2026-05-01,2026-05-31,Net 30,HQ,Grouped memo,Support,Monthly support,1,25,25,N,,2026-05-02");
    }

    private String[] headers() {
        return Arrays.stream(NormalizedInvoiceField.values())
                .map(NormalizedInvoiceField::sampleHeader)
                .toArray(String[]::new);
    }

    private record RunSaveSnapshot(int attemptedRows, int skippedRows, int importedRows, Instant completedAt) {
    }
}
