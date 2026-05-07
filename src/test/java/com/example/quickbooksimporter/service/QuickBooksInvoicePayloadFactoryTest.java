package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuickBooksInvoicePayloadFactoryTest {

    private final QuickBooksInvoicePayloadFactory factory = new QuickBooksInvoicePayloadFactory();

    @Test
    void buildsInvoicePayloadFromNormalizedInvoice() {
        NormalizedInvoice invoice = new NormalizedInvoice(
                "INV-100",
                "Acme",
                LocalDate.of(2026, 5, 7),
                LocalDate.of(2026, 6, 7),
                "Net 30",
                null,
                "Test memo",
                List.of(new InvoiceLine("Consulting", "Implementation", new BigDecimal("2"), new BigDecimal("50"),
                        new BigDecimal("100"), false, BigDecimal.ZERO, LocalDate.of(2026, 5, 7))));

        Map<String, Object> payload = factory.build(invoice, Map.of("value", "1"), Map.of("value", "2"));

        assertThat(payload).containsEntry("DocNumber", "INV-100");
        assertThat((Map<String, Object>) payload.get("CustomerRef")).containsEntry("value", "1");
        Map<String, Object> line = ((List<Map<String, Object>>) payload.get("Line")).getFirst();
        assertThat(line).containsEntry("Amount", new BigDecimal("100"));
        assertThat((Map<String, Object>) line.get("SalesItemLineDetail")).containsEntry("ItemRef", Map.of("value", "2"));
    }
}
