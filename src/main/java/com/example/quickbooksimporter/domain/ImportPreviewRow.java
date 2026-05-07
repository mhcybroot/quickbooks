package com.example.quickbooksimporter.domain;

public record ImportPreviewRow(
        int rowNumber,
        String invoiceNo,
        String customer,
        String itemName,
        ImportRowStatus status,
        String message) {
}
