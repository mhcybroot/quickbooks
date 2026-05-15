package com.example.quickbooksimporter.domain;

public record PaymentImportPreviewRow(
        int rowNumber,
        String customer,
        String invoiceNo,
        String referenceNo,
        ImportRowStatus status,
        String message) implements com.example.quickbooksimporter.service.ImportPreviewSummary.ImportPreviewStatusRow {
}
