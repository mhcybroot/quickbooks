package com.example.quickbooksimporter.domain;

import java.util.List;
import java.util.Map;

public record SalesReceiptImportPreview(
        String sourceFileName,
        Map<NormalizedSalesReceiptField, String> mapping,
        List<String> headers,
        List<SalesReceiptImportPreviewRow> rows,
        List<SalesReceiptRowValidationResult> validations) {
}
