package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvoiceImportValidatorTest {

    @Test
    void marksExistingInvoiceAsInvalid() {
        QuickBooksConnectionService connectionService = mock(QuickBooksConnectionService.class);
        QuickBooksGateway gateway = mock(QuickBooksGateway.class);
        QboConnectionEntity connection = new QboConnectionEntity();
        connection.setRealmId("123");
        connection.setAccessToken("token");
        connection.setRefreshToken("refresh");
        connection.setExpiresAt(Instant.now().plusSeconds(600));
        connection.setConnectedAt(Instant.now());
        connection.setUpdatedAt(Instant.now());
        when(connectionService.getConnection()).thenReturn(Optional.of(connection));
        when(gateway.invoiceExists("123", "INV-1")).thenReturn(true);

        InvoiceImportValidator validator = new InvoiceImportValidator(connectionService, gateway);
        NormalizedInvoice invoice = invoice("INV-1", false, BigDecimal.ZERO);

        var result = validator.validate(2, Map.of("*InvoiceNo", "INV-1"), invoice);

        assertThat(result.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(result.message()).contains("already exists");
    }

    @Test
    void blocksPositiveTaxRatesInV1() {
        QuickBooksConnectionService connectionService = mock(QuickBooksConnectionService.class);
        QuickBooksGateway gateway = mock(QuickBooksGateway.class);
        when(connectionService.getConnection()).thenReturn(Optional.empty());

        InvoiceImportValidator validator = new InvoiceImportValidator(connectionService, gateway);
        NormalizedInvoice invoice = invoice("INV-2", true, new BigDecimal("5"));

        var result = validator.validate(3, Map.of("*InvoiceNo", "INV-2"), invoice);

        assertThat(result.status()).isEqualTo(ImportRowStatus.INVALID);
        assertThat(result.message()).contains("not supported");
    }

    private NormalizedInvoice invoice(String invoiceNo, boolean taxable, BigDecimal taxRate) {
        return new NormalizedInvoice(
                invoiceNo,
                "Acme",
                LocalDate.of(2026, 5, 7),
                LocalDate.of(2026, 6, 7),
                "Net 30",
                null,
                "Memo",
                List.of(new InvoiceLine("Consulting", "", BigDecimal.ONE, new BigDecimal("10"),
                        new BigDecimal("10"), taxable, taxRate, LocalDate.of(2026, 5, 7))));
    }
}
