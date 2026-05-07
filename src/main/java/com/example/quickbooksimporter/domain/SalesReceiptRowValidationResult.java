package com.example.quickbooksimporter.domain;

import java.util.Map;

public record SalesReceiptRowValidationResult(
        int rowNumber,
        ParsedCsvRow parsedRow,
        NormalizedSalesReceipt salesReceipt,
        ImportRowStatus status,
        String message,
        Map<String, String> rawData) {
}
