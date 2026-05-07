package com.example.quickbooksimporter.domain;

import java.util.Map;

public record PaymentRowValidationResult(
        int rowNumber,
        ParsedCsvRow parsedRow,
        NormalizedPayment payment,
        ImportRowStatus status,
        String message,
        Map<String, String> rawData) {
}
