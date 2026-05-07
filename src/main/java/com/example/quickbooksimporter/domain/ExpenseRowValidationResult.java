package com.example.quickbooksimporter.domain;

import java.util.Map;

public record ExpenseRowValidationResult(
        int rowNumber,
        ParsedCsvRow parsedRow,
        NormalizedExpense expense,
        ImportRowStatus status,
        String message,
        Map<String, String> rawData) {
}
