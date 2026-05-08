package com.example.quickbooksimporter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    @Test
    void parsesCsvWithMissingHeaderNames() {
        String csv = "sl. No,Invoice #,Invoice Date,Client,,,\n"
                + "1,INV-1001,05/01/2026,Acme Corp,CHK-01,0\n";

        ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(document.headers()).contains("sl. No", "Invoice #", "Invoice Date", "Client");
        assertThat(document.headers()).contains("_unnamed_column", "_unnamed_column_2");
        assertThat(document.rows()).hasSize(1);
        assertThat(document.rows().getFirst().values()).containsEntry("Invoice #", "INV-1001");
        assertThat(document.rows().getFirst().values()).containsEntry("_unnamed_column", "CHK-01");
    }
}
