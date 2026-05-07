package com.example.quickbooksimporter.domain;

public record ExpenseImportPreviewRow(
        int rowNumber,
        String vendor,
        String category,
        String referenceNo,
        ImportRowStatus status,
        String message) {
}
