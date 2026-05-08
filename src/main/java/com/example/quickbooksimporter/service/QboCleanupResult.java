package com.example.quickbooksimporter.service;

public record QboCleanupResult(
        String recordId,
        String externalNumber,
        String action,
        boolean success,
        String message,
        String intuitTid) {
}
