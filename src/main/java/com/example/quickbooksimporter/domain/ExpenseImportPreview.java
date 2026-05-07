package com.example.quickbooksimporter.domain;

import java.util.List;
import java.util.Map;

public record ExpenseImportPreview(
        String sourceFileName,
        Map<NormalizedExpenseField, String> mapping,
        List<String> headers,
        List<ExpenseImportPreviewRow> rows,
        List<ExpenseRowValidationResult> validations) {
}
