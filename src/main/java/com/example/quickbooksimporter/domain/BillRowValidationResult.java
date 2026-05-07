package com.example.quickbooksimporter.domain;

import java.util.Map;

public record BillRowValidationResult(
        int rowNumber,
        ParsedCsvRow parsedRow,
        NormalizedBill bill,
        ImportRowStatus status,
        String message,
        Map<String, String> rawData) {
}
