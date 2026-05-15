package com.example.quickbooksimporter.domain;

public record BillImportPreviewRow(
        int rowNumber,
        String billNo,
        String vendor,
        int lineCount,
        ImportRowStatus status,
        String message) implements com.example.quickbooksimporter.service.ImportPreviewSummary.ImportPreviewStatusRow {
}
