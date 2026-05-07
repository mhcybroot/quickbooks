package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.InvoiceLine;
import com.example.quickbooksimporter.domain.NormalizedInvoice;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QuickBooksInvoicePayloadFactory {

    public Map<String, Object> build(NormalizedInvoice invoice,
                                     Map<String, Object> customerRef,
                                     Map<String, Object> itemRef) {
        InvoiceLine line = invoice.lines().getFirst();
        return Map.of(
                "DocNumber", invoice.invoiceNo(),
                "TxnDate", invoice.invoiceDate().toString(),
                "DueDate", invoice.dueDate().toString(),
                "CustomerMemo", Map.of("value", invoice.memo() == null ? "" : invoice.memo()),
                "CustomerRef", customerRef,
                "Line", List.of(Map.of(
                        "Amount", line.amount(),
                        "DetailType", "SalesItemLineDetail",
                        "Description", line.description() == null ? "" : line.description(),
                        "SalesItemLineDetail", Map.of(
                                "Qty", defaultDecimal(line.quantity()),
                                "UnitPrice", defaultDecimal(line.rate()),
                                "ItemRef", itemRef,
                                "TaxCodeRef", Map.of("value", line.taxable() ? "TAX" : "NON")))));
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value;
    }
}
