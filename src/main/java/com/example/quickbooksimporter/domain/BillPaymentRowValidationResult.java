package com.example.quickbooksimporter.domain;

import java.util.Map;

public record BillPaymentRowValidationResult(
        int rowNumber,
        ParsedCsvRow parsedRow,
        NormalizedBillPayment payment,
        ImportRowStatus status,
        String message,
        Map<String, String> rawData) {
}
