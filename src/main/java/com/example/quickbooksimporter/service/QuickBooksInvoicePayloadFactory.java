package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class QuickBooksInvoicePayloadFactory {

    public Map<String, Object> build(NormalizedInvoice invoice,
                                     Map<String, Object> customerRef,
                                     Function<InvoiceLine, Map<String, Object>> itemRefResolver) {
        return Map.of(
                "DocNumber", invoice.invoiceNo(),
                "TxnDate", invoice.invoiceDate().toString(),
                "DueDate", invoice.dueDate().toString(),
                "CustomerMemo", Map.of("value", invoice.memo() == null ? "" : invoice.memo()),
                "CustomerRef", customerRef,
                "Line", invoice.lines().stream()
                        .map(line -> Map.of(
                                "Amount", line.amount(),
                                "DetailType", "SalesItemLineDetail",
                                "Description", line.description() == null ? "" : line.description(),
                                "SalesItemLineDetail", Map.of(
                                        "Qty", defaultDecimal(line.quantity()),
                                        "UnitPrice", defaultDecimal(line.rate()),
                                        "ItemRef", itemRefResolver.apply(line),
                                        "TaxCodeRef", Map.of("value", line.taxable() ? "TAX" : "NON"))))
                        .toList());
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value;
    }
}
