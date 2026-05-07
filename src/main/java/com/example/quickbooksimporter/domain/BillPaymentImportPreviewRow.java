package com.example.quickbooksimporter.domain;

public record BillPaymentImportPreviewRow(
        int rowNumber,
        String vendor,
        String billNo,
        String referenceNo,
        ImportRowStatus status,
        String message) {
}
