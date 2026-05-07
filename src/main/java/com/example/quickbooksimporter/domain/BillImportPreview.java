package com.example.quickbooksimporter.domain;

import java.util.List;
import java.util.Map;

public record BillImportPreview(
        String sourceFileName,
        Map<NormalizedBillField, String> mapping,
        List<String> headers,
        List<BillImportPreviewRow> rows,
        List<BillRowValidationResult> validations) {
}
