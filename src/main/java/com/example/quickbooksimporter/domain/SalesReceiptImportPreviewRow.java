package com.example.quickbooksimporter.domain;

public record SalesReceiptImportPreviewRow(
        int rowNumber,
        String receiptNo,
        String customer,
        int lineCount,
        ImportRowStatus status,
        String message) {
}
