package com.example.quickbooksimporter.service;

public record QboCleanupResult(
        String recordId,
        String externalNumber,
        String source,
        String parentExternalNumber,
        String action,
        boolean success,
        String message,
        String intuitTid) {
}
