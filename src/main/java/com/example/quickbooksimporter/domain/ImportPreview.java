package com.example.quickbooksimporter.domain;

import java.util.List;
import java.util.Map;

public record ImportPreview(
        String sourceFileName,
        Map<NormalizedInvoiceField, String> mapping,
        List<String> headers,
        List<ImportPreviewRow> rows,
        List<RowValidationResult> validations,
        String exportCsv,
        boolean groupingEnabled) {
}
