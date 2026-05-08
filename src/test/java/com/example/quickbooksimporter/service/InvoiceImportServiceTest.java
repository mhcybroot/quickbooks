package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.domain.RowValidationResult;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
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

    @BeforeEach
    void setUp() {
        service = new InvoiceImportService(
                new InvoiceCsvParser(),
                new InvoiceRowMapper(),
                validator,
                new CsvTemplateService(),
                importRunRepository,
                new ObjectMapper(),
                connectionService,
                quickBooksGateway,
                mock(QuickBooksProperties.class));
        when(validator.validate(anyInt(), anyMap(), any(NormalizedInvoice.class)))
                .thenAnswer(invocation -> new RowValidationResult(
                        invocation.getArgument(0),
                        new com.example.quickbooksimporter.domain.ParsedCsvRow(invocation.getArgument(0), invocation.getArgument(1)),
                        invocation.getArgument(2),
                        ImportRowStatus.READY,
                        "",
                        invocation.getArgument(1)));
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
}
