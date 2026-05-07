package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

class InvoiceCsvParserTest {

    private final InvoiceCsvParser parser = new InvoiceCsvParser();

    @Test
    void parsesSampleInvoiceCsv() {
        InputStream inputStream = getClass().getResourceAsStream("/sample_invoice_import_tax. (1).csv");

        ParsedCsvDocument document = parser.parse(inputStream);

        assertThat(document.headers()).contains("*InvoiceNo", "*Customer", "Item(Product/Service)");
        assertThat(document.rows()).hasSize(1);
        assertThat(document.rows().getFirst().values()).containsEntry("*InvoiceNo", "1202");
        assertThat(document.rows().getFirst().values()).containsEntry("Taxable", "N");
    }
}
