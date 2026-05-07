package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.NormalizedInvoice;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

@Service
public class CsvTemplateService {

    public String exportInvoices(List<NormalizedInvoice> invoices) {
        try {
            StringWriter writer = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(writer,
                    CSVFormat.DEFAULT.withHeader(headers()))) {
                for (NormalizedInvoice invoice : invoices) {
                    invoice.lines().forEach(line -> {
                        try {
                            printer.printRecord(
                                    invoice.invoiceNo(),
                                    invoice.customer(),
                                    invoice.invoiceDate(),
                                    invoice.dueDate(),
                                    invoice.terms(),
                                    invoice.location(),
                                    invoice.memo(),
                                    line.itemName(),
                                    line.description(),
                                    line.quantity(),
                                    line.rate(),
                                    line.amount(),
                                    line.taxable() ? "Y" : "N",
                                    line.taxRate() == null ? "" : line.taxRate().stripTrailingZeros().toPlainString() + "%",
                                    line.serviceDate());
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
                }
            }
            return writer.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export CSV", exception);
        }
    }

    public String[] headers() {
        return java.util.Arrays.stream(NormalizedInvoiceField.values())
                .map(NormalizedInvoiceField::sampleHeader)
                .toArray(String[]::new);
    }
}
