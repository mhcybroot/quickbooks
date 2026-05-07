package com.example.quickbooksimporter.domain;

import java.util.Map;

public record RowValidationResult(
        int rowNumber,
        ParsedCsvRow parsedRow,
        NormalizedInvoice invoice,
        ImportRowStatus status,
        String message,
        Map<String, String> rawData) {
}
