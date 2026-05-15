package com.example.quickbooksimporter.service;

public record QuickBooksBatchCreateResult(
        boolean success,
        String entityId,
        String referenceNumber,
        String message,
        String intuitTid) {
}
