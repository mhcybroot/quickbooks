package com.example.quickbooksimporter.service;

import java.util.Map;

public record ImportPreviewOptions(
        Boolean invoiceGroupingEnabled,
        Map<String, QuickBooksInvoiceRef> draftInvoiceRefs) {

    public static ImportPreviewOptions defaults() {
        return new ImportPreviewOptions(null, Map.of());
    }
}
